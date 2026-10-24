package ru.vkr.materials.service;

import java.util.List;

public record ReportTable(String title, String description, List<String> columns, List<List<Object>> rows) {
    public String display(Object value) {
        if (value instanceof java.math.BigDecimal number)
            return number.stripTrailingZeros().toPlainString();
        return value == null ? "" : value.toString();
    }

    public static List<Object> row(Object... values) {
        return java.util.Arrays.stream(values).map(v -> v == null ? (Object) "" : v).toList();
    }
}
