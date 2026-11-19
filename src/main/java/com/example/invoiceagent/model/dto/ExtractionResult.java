package com.example.invoiceagent.model.dto;

import java.util.List;

public record ExtractionResult(String extractor, ExtractedInvoice invoice, List<Finding> findings) {

    public boolean hasErrors() {
        return findings.stream().anyMatch(Finding::isError);
    }

    public boolean needsReview() {
        return findings.stream().anyMatch(Finding::needsReview);
    }
}
