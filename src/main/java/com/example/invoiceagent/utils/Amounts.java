package com.example.invoiceagent.utils;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public class Amounts {

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.replaceAll("[^0-9,.\\-]", "");
        if (s.isEmpty()) return null;
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma > lastDot) {
            s = s.replace(".", "").replace(',', '.');
        } else {
            s = s.replace(",", "");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String fmt(BigDecimal value) {
        return value == null ? "?" : money(value).toPlainString();
    }
}
