package com.example.invoiceagent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record Finding(Severity severity, String code, String message) {
    public enum Severity { ERROR, REVIEW, WARNING }

    public static Finding error(String code, String message) {
        return new Finding(Severity.ERROR, code, message);
    }

    public static Finding review(String code, String message) {
        return new Finding(Severity.REVIEW, code, message);
    }

    public static Finding warning(String code, String message) {
        return new Finding(Severity.WARNING, code, message);
    }

    @JsonIgnore
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean needsReview() {
        return severity != Severity.WARNING;
    }
}
