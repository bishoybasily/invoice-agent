package com.example.invoiceagent.ops.xrechnung;

import com.example.invoiceagent.model.dto.ExtractedInvoice;
import com.example.invoiceagent.model.dto.ExtractedInvoice.Line;
import com.example.invoiceagent.model.dto.ExtractedInvoice.Party;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.invoiceagent.utils.Amounts.money;
import static com.example.invoiceagent.utils.Amounts.parse;

@Component
public class XRechnungWriter {

    static final String UBL = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String CUSTOMIZATION_ID = "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";
    static final String PROFILE_ID = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";

    private static final Pattern CITY = Pattern.compile("^(\\d{4,5}(?: [A-Z]{2})?)\\s+(.+?)(?:,\\s*([A-Z]{2}))?$");

    public String write(ExtractedInvoice inv) {
        try {
            Document doc = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElementNS(UBL, "ubl:Invoice");
            root.setAttribute("xmlns:cac", CAC);
            root.setAttribute("xmlns:cbc", CBC);
            doc.appendChild(root);
            Xml x = new Xml(doc);
            String cur = inv.currency().isBlank() ? "EUR" : inv.currency();

            x.cbc(root, "CustomizationID", CUSTOMIZATION_ID);
            x.cbc(root, "ProfileID", PROFILE_ID);
            x.cbc(root, "ID", inv.invoiceNumber());
            x.cbc(root, "IssueDate", inv.invoiceDate());
            if (!inv.dueDate().isBlank()) x.cbc(root, "DueDate", inv.dueDate());
            x.cbc(root, "InvoiceTypeCode", "380");
            if (!inv.note().isBlank()) x.cbc(root, "Note", inv.note());
            x.cbc(root, "DocumentCurrencyCode", cur);
            x.cbc(root, "BuyerReference", firstNonBlank(inv.buyerReference(), inv.orderReference(), "n/a"));
            if (!inv.orderReference().isBlank()) {
                x.cbc(x.cac(root, "OrderReference"), "ID", inv.orderReference());
            }

            party(x, x.cac(root, "AccountingSupplierParty"), inv.seller());
            party(x, x.cac(root, "AccountingCustomerParty"), inv.buyer());

            if (!inv.seller().iban().isBlank()) {
                Element means = x.cac(root, "PaymentMeans");
                x.cbc(means, "PaymentMeansCode", "58");
                x.cbc(x.cac(means, "PayeeFinancialAccount"), "ID", inv.seller().iban().replaceAll("\\s", ""));
            }

            Map<BigDecimal, BigDecimal> netPerRate = new TreeMap<>();
            BigDecimal net = BigDecimal.ZERO;
            for (Line l : inv.lines()) {
                BigDecimal amount = parse(l.netAmount());
                net = net.add(amount);
                netPerRate.merge(parse(l.vatRate()).stripTrailingZeros(), amount, BigDecimal::add);
            }
            BigDecimal vat = BigDecimal.ZERO;
            Element taxTotal = x.cac(root, "TaxTotal");
            Element taxAmount = x.amount(taxTotal, "TaxAmount", BigDecimal.ZERO, cur);
            for (var e : netPerRate.entrySet()) {
                BigDecimal rateVat = money(e.getValue().multiply(e.getKey()).movePointLeft(2));
                vat = vat.add(rateVat);
                Element sub = x.cac(taxTotal, "TaxSubtotal");
                x.amount(sub, "TaxableAmount", e.getValue(), cur);
                x.amount(sub, "TaxAmount", rateVat, cur);
                Element cat = x.cac(sub, "TaxCategory");
                x.cbc(cat, "ID", taxCategory(e.getKey()));
                x.cbc(cat, "Percent", e.getKey().toPlainString());
                if (e.getKey().signum() == 0) {
                    x.cbc(cat, "TaxExemptionReason", "Reverse charge");
                }
                x.cbc(x.cac(cat, "TaxScheme"), "ID", "VAT");
            }
            taxAmount.setTextContent(money(vat).toPlainString());

            Element totals = x.cac(root, "LegalMonetaryTotal");
            x.amount(totals, "LineExtensionAmount", net, cur);
            x.amount(totals, "TaxExclusiveAmount", net, cur);
            x.amount(totals, "TaxInclusiveAmount", net.add(vat), cur);
            x.amount(totals, "PayableAmount", net.add(vat), cur);

            int pos = 1;
            for (Line l : inv.lines()) {
                Element line = x.cac(root, "InvoiceLine");
                x.cbc(line, "ID", String.valueOf(pos++));
                Element qty = x.cbc(line, "InvoicedQuantity", parse(l.quantity()).stripTrailingZeros().toPlainString());
                qty.setAttribute("unitCode", "C62");
                x.amount(line, "LineExtensionAmount", parse(l.netAmount()), cur);
                Element item = x.cac(line, "Item");
                x.cbc(item, "Name", l.description());
                Element cat = x.cac(item, "ClassifiedTaxCategory");
                BigDecimal rate = parse(l.vatRate()).stripTrailingZeros();
                x.cbc(cat, "ID", taxCategory(rate));
                x.cbc(cat, "Percent", rate.toPlainString());
                x.cbc(x.cac(cat, "TaxScheme"), "ID", "VAT");
                x.amount(x.cac(line, "Price"), "PriceAmount", parse(l.unitPrice()), cur);
            }

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not write XRechnung", e);
        }
    }

    private void party(Xml x, Element wrapper, Party p) {
        Element party = x.cac(wrapper, "Party");
        Element address = x.cac(party, "PostalAddress");
        x.cbc(address, "StreetName", p.street());
        Matcher m = CITY.matcher(p.city().trim());
        String country = p.vatId().length() >= 2 ? p.vatId().substring(0, 2).toUpperCase() : "DE";
        if (m.matches()) {
            x.cbc(address, "CityName", m.group(2));
            x.cbc(address, "PostalZone", m.group(1));
            if (m.group(3) != null) country = m.group(3);
        } else {
            x.cbc(address, "CityName", p.city());
        }
        x.cbc(x.cac(address, "Country"), "IdentificationCode", country);
        if (!p.vatId().isBlank()) {
            Element tax = x.cac(party, "PartyTaxScheme");
            x.cbc(tax, "CompanyID", p.vatId().replaceAll("\\s", ""));
            x.cbc(x.cac(tax, "TaxScheme"), "ID", "VAT");
        }
        x.cbc(x.cac(party, "PartyLegalEntity"), "RegistrationName", p.name());
    }

    private static String taxCategory(BigDecimal rate) {
        return rate.signum() == 0 ? "AE" : "S";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private record Xml(Document doc) {
        Element cbc(Element parent, String name, String text) {
            Element e = doc.createElementNS(CBC, "cbc:" + name);
            e.setTextContent(text);
            parent.appendChild(e);
            return e;
        }

        Element cac(Element parent, String name) {
            Element e = doc.createElementNS(CAC, "cac:" + name);
            parent.appendChild(e);
            return e;
        }

        Element amount(Element parent, String name, BigDecimal value, String currency) {
            Element e = cbc(parent, name, money(value).toPlainString());
            e.setAttribute("currencyID", currency);
            return e;
        }
    }
}
