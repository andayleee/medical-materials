package ru.vkr.materials.export;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ru.vkr.materials.service.ReportTable;

public final class CsvExport {
    private CsvExport() {
    }

    public static byte[] encode(ReportTable table) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        append(csv, new ArrayList<>(table.columns()));
        for (var row : table.rows())
            append(csv, row);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder csv, List<?> values) {
        StringJoiner line = new StringJoiner(";");
        for (Object value : values) {
            String text = value == null ? ""
                    : value instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString().replace('.', ',')
                            : value.toString();
            if (value instanceof CharSequence && dangerous(text))
                text = "'" + text;
            line.add("\"" + text.replace("\"", "\"\"") + "\"");
        }
        csv.append(line).append("\r\n");
    }

    private static boolean dangerous(String text) {
        String trimmed = text.stripLeading();
        return !trimmed.isEmpty() && "=+-@\t\r\n".indexOf(trimmed.charAt(0)) >= 0
                || (!text.isEmpty() && "\t\r\n".indexOf(text.charAt(0)) >= 0);
    }
}
