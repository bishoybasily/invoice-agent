package com.example.invoiceagent.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ExtractedInvoice(
        Party seller,
        Party buyer,
        @JsonPropertyDescription("Invoice number exactly as printed") String invoiceNumber,
        @JsonPropertyDescription("Invoice date, ISO format yyyy-MM-dd") String invoiceDate,
        @JsonPropertyDescription("Due date, ISO format yyyy-MM-dd, empty if none is printed") String dueDate,
        @JsonPropertyDescription("Purchase order / contract reference, empty if none") String orderReference,
        @JsonPropertyDescription("Buyer reference or Leitweg-ID, empty if none") String buyerReference,
        @JsonPropertyDescription("ISO 4217 currency code, e.g. EUR") String currency,
        List<Line> lines,
        List<VatSubtotal> vatBreakdown,
        @JsonPropertyDescription("Net total as printed, dot as decimal separator, no thousands separator") String netTotal,
        @JsonPropertyDescription("Total VAT as printed, dot decimal") String vatTotal,
        @JsonPropertyDescription("Gross total / amount due as PRINTED on the invoice, even if it looks wrong. Dot decimal") String grossTotal,
        @JsonPropertyDescription("Free-text legal note such as a reverse-charge statement, empty if none") String note,
        @JsonPropertyDescription("Your confidence from 0.0 to 1.0 that every field above is read correctly") double confidence,
        @JsonPropertyDescription("Names of fields you were unsure about, e.g. because the scan is blurry. Empty list if none") List<String> uncertainFields
) {
    public ExtractedInvoice {
        uncertainFields = uncertainFields == null ? List.of() : List.copyOf(uncertainFields);
    }

    public record Party(
            String name,
            String street,
            @JsonPropertyDescription("Postal code and city as printed, e.g. '70173 Stuttgart'") String city,
            @JsonPropertyDescription("VAT ID (USt-IdNr.) without spaces, empty if none is printed") String vatId,
            @JsonPropertyDescription("IBAN as printed, empty if none") String iban) {
    }

    public record Line(
            String description,
            @JsonPropertyDescription("Quantity, dot decimal") String quantity,
            @JsonPropertyDescription("Unit net price, dot decimal") String unitPrice,
            @JsonPropertyDescription("Line net amount as printed, dot decimal") String netAmount,
            @JsonPropertyDescription("VAT rate in percent, e.g. '19', '7' or '0'") String vatRate) {
    }

    public record VatSubtotal(
            @JsonPropertyDescription("VAT rate in percent") String vatRate,
            @JsonPropertyDescription("Taxable amount for this rate, dot decimal") String taxableAmount,
            @JsonPropertyDescription("VAT amount for this rate as printed, dot decimal") String vatAmount) {
    }
}
