package com.example.invoiceagent.ops.extraction;

import com.example.invoiceagent.model.dto.ExtractedInvoice;

public interface InvoiceExtractor {

    String getLabel();

    ExtractedInvoice extract(String fileName, byte[] pdf);
}
