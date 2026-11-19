package com.example.invoiceagent.ops.validation;

import com.example.invoiceagent.config.OpsProps;
import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.dto.ExtractedInvoice.Line;
import com.example.invoiceagent.model.dto.ExtractedInvoice.VatSubtotal;
import com.example.invoiceagent.model.dto.Finding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.example.invoiceagent.utils.Amounts.fmt;
import static com.example.invoiceagent.utils.Amounts.money;
import static com.example.invoiceagent.utils.Amounts.parse;

@Component
@RequiredArgsConstructor
public class InvoiceValidator {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static final Map<String, Pattern> VAT_ID_FORMATS = Map.of(
            "DE", Pattern.compile("DE\\d{9}"),
            "AT", Pattern.compile("ATU\\d{8}"),
            "NL", Pattern.compile("NL\\d{9}B\\d{2}"),
            "FR", Pattern.compile("FR[0-9A-Z]{2}\\d{9}"),
            "BE", Pattern.compile("BE[01]\\d{9}"));
    private static final Pattern ANY_VAT_ID = Pattern.compile("[A-Z]{2}[0-9A-Z]{2,13}");

    private final OpsProps props;

    public List<Finding> validate(ExtractedInvoice inv) {
        List<Finding> findings = new ArrayList<>();
        requiredFields(inv, findings);
        lineArithmetic(inv, findings);
        totals(inv, findings);
        vatIds(inv, findings);
        reverseCharge(inv, findings);
        iban(inv, findings);
        confidence(inv, findings);
        return findings;
    }

    private void confidence(ExtractedInvoice inv, List<Finding> f) {
        if (inv.confidence() < props.getMinConfidence() || !inv.uncertainFields().isEmpty()) {
            String unsure = inv.uncertainFields().isEmpty() ? "" : ", unsure about: " + String.join(", ", inv.uncertainFields());
            f.add(Finding.review("LOW-CONFIDENCE", "extractor confidence " + inv.confidence() + unsure));
        }
    }

    private void requiredFields(ExtractedInvoice inv, List<Finding> f) {
        if (blank(inv.invoiceNumber())) f.add(Finding.error("BR-02", "invoice number missing"));
        if (date(inv.invoiceDate()) == null) f.add(Finding.error("BR-03", "invoice date missing or not a date"));
        if (inv.seller() == null || blank(inv.seller().name())) f.add(Finding.error("BR-06", "seller name missing"));
        if (inv.buyer() == null || blank(inv.buyer().name())) f.add(Finding.error("BR-07", "buyer name missing"));
        if (blank(inv.currency())) f.add(Finding.error("BR-05", "currency missing"));
        if (inv.lines() == null || inv.lines().isEmpty()) f.add(Finding.error("BR-16", "no invoice lines"));
        if (blank(inv.dueDate())) {
            f.add(Finding.error("BR-CO-25", "no due date / payment terms"));
        } else if (date(inv.dueDate()) == null) {
            f.add(Finding.error("BR-CO-25", "due date is not a valid date: " + inv.dueDate()));
        } else if (date(inv.invoiceDate()) != null && date(inv.dueDate()).isBefore(date(inv.invoiceDate()))) {
            f.add(Finding.warning("DUE-BEFORE-ISSUE", "due date is before the invoice date"));
        }
    }

    private void lineArithmetic(ExtractedInvoice inv, List<Finding> f) {
        if (inv.lines() == null) return;
        int pos = 1;
        for (Line line : inv.lines()) {
            BigDecimal qty = parse(line.quantity());
            BigDecimal price = parse(line.unitPrice());
            BigDecimal net = parse(line.netAmount());
            if (qty == null || price == null || net == null) {
                f.add(Finding.error("LINE-UNREADABLE", "line " + pos + ": quantity, price or amount missing"));
            } else if (money(qty.multiply(price)).subtract(net).abs().compareTo(TOLERANCE) > 0) {
                f.add(Finding.error("LINE-AMOUNT", "line " + pos + ": " + fmt(qty) + " x " + fmt(price)
                        + " = " + fmt(qty.multiply(price)) + " but line says " + fmt(net)));
            }
            if (parse(line.vatRate()) == null) {
                f.add(Finding.error("BR-CO-04", "line " + pos + ": VAT rate missing"));
            }
            pos++;
        }
    }

    private void totals(ExtractedInvoice inv, List<Finding> f) {
        if (inv.lines() == null || inv.lines().isEmpty()) return;

        Map<BigDecimal, BigDecimal> netPerRate = new TreeMap<>();
        BigDecimal netSum = BigDecimal.ZERO;
        for (Line line : inv.lines()) {
            BigDecimal net = parse(line.netAmount());
            BigDecimal rate = parse(line.vatRate());
            if (net == null || rate == null) return;
            netSum = netSum.add(net);
            netPerRate.merge(rate.stripTrailingZeros(), net, BigDecimal::add);
        }
        BigDecimal vatSum = BigDecimal.ZERO;
        for (var e : netPerRate.entrySet()) {
            vatSum = vatSum.add(money(e.getValue().multiply(e.getKey()).movePointLeft(2)));
        }
        BigDecimal grossComputed = money(netSum.add(vatSum));

        BigDecimal netPrinted = parse(inv.netTotal());
        BigDecimal vatPrinted = parse(inv.vatTotal());
        BigDecimal grossPrinted = parse(inv.grossTotal());

        if (netPrinted != null && differs(netPrinted, netSum)) {
            f.add(Finding.error("BR-CO-10", "net total " + fmt(netPrinted) + " != sum of lines " + fmt(netSum)));
        }
        if (vatPrinted != null && differs(vatPrinted, vatSum)) {
            f.add(Finding.error("BR-CO-14", "VAT total " + fmt(vatPrinted) + " != computed VAT " + fmt(vatSum)));
        }
        if (grossPrinted == null) {
            f.add(Finding.error("BR-14", "gross total missing"));
        } else if (differs(grossPrinted, grossComputed)) {
            f.add(Finding.error("BR-CO-15", "printed total " + fmt(grossPrinted) + " != computed total " + fmt(grossComputed)));
        }

        if (inv.vatBreakdown() != null) {
            for (VatSubtotal sub : inv.vatBreakdown()) {
                BigDecimal rate = parse(sub.vatRate());
                BigDecimal taxable = parse(sub.taxableAmount());
                BigDecimal vat = parse(sub.vatAmount());
                if (rate == null || taxable == null || vat == null) continue;
                BigDecimal expectedTaxable = netPerRate.get(rate.stripTrailingZeros());
                if (expectedTaxable == null || differs(taxable, expectedTaxable)) {
                    f.add(Finding.error("BR-CO-17", "VAT " + rate.stripTrailingZeros().toPlainString()
                            + " %: taxable amount " + fmt(taxable) + " does not match the lines"));
                } else if (differs(vat, money(taxable.multiply(rate).movePointLeft(2)))) {
                    f.add(Finding.error("BR-CO-17", "VAT " + rate.stripTrailingZeros().toPlainString()
                            + " % on " + fmt(taxable) + " should be " + fmt(taxable.multiply(rate).movePointLeft(2))
                            + ", invoice says " + fmt(vat)));
                }
            }
        }
    }

    private void vatIds(ExtractedInvoice inv, List<Finding> f) {
        if (inv.seller() == null) return;
        String sellerVat = normalize(inv.seller().vatId());
        if (sellerVat.isEmpty()) {
            f.add(Finding.error("BR-CO-26", "seller VAT ID / tax number missing (required on German invoices)"));
        } else {
            checkVatFormat("seller", sellerVat, f);
        }
        if (inv.buyer() != null && !blank(inv.buyer().vatId())) {
            checkVatFormat("buyer", normalize(inv.buyer().vatId()), f);
        }
    }

    private void checkVatFormat(String who, String vatId, List<Finding> f) {
        Pattern p = VAT_ID_FORMATS.getOrDefault(vatId.length() >= 2 ? vatId.substring(0, 2) : "", ANY_VAT_ID);
        if (!p.matcher(vatId).matches()) {
            f.add(Finding.error("VAT-ID-FORMAT", who + " VAT ID " + vatId + " has an invalid format"));
        }
    }

    private void reverseCharge(ExtractedInvoice inv, List<Finding> f) {
        if (inv.lines() == null) return;
        boolean hasZeroRate = inv.lines().stream()
                .map(l -> parse(l.vatRate()))
                .anyMatch(r -> r != null && r.signum() == 0);
        if (!hasZeroRate) return;
        String note = inv.note() == null ? "" : inv.note().toLowerCase();
        boolean mentionsReverseCharge = note.contains("reverse charge") || note.contains("steuerschuldnerschaft")
                || note.contains("196");
        if (!mentionsReverseCharge) {
            f.add(Finding.error("BR-AE-10", "0 % VAT without a reverse-charge or exemption note"));
        }
        if (inv.buyer() == null || blank(inv.buyer().vatId())) {
            f.add(Finding.error("BR-AE-02", "reverse charge requires the buyer's VAT ID"));
        }
    }

    private void iban(ExtractedInvoice inv, List<Finding> f) {
        if (inv.seller() == null || blank(inv.seller().iban())) {
            f.add(Finding.warning("NO-IBAN", "no seller IBAN, payment details must come from master data"));
            return;
        }
        String iban = normalize(inv.seller().iban());
        if (!ibanChecksumOk(iban)) {
            f.add(Finding.error("IBAN-CHECKSUM", "seller IBAN " + iban + " fails the mod-97 check"));
        }
    }

    static boolean ibanChecksumOk(String iban) {
        if (!iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}")) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return money(a).subtract(money(b)).abs().compareTo(TOLERANCE) > 0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s", "").toUpperCase();
    }

    private static LocalDate date(String s) {
        if (blank(s)) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
