package ru.vkr.materials.service;


public final class Barcode {
    private Barcode() {
    }

    public static String document(long id) {
        return value("20", id);
    }

    private static String value(String type, long id) {
        if (id < 1 || id > 999999999999L)
            throw new IllegalArgumentException("Идентификатор вне диапазона штрихкода");
        return type + String.format(java.util.Locale.ROOT, "%012d", id);
    }

    public static boolean isBatchNumber(String value) {
        return value != null && value.matches("[0-9]{12}") && !value.equals("000000000000");
    }

    public static Long documentId(String value) {
        return parse(value, "20");
    }

    private static Long parse(String value, String type) {
        if (value == null || !value.strip().matches(type + "[0-9]{12}"))
            return null;
        long id = Long.parseLong(value.strip().substring(2));
        return id == 0 ? null : id;
    }
}
