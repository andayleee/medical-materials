package ru.vkr.materials.service;

import java.util.*;


public final class Code128 {
    private Code128() {
    }

    private static final String[] PATTERNS = ("212222 222122 222221 121223 121322 131222 122213 122312 132212 221213 221312 231212 112232 122132 122231 113222 123122 123221 223211 221132 221231 213212 223112 312131 311222 321122 321221 312212 322112 322211 212123 212321 232121 111323 131123 131321 112313 132113 132311 211313 231113 231311 112133 112331 132131 113123 113321 133121 313121 211331 231131 213113 213311 213131 311123 311321 331121 312113 312311 332111 314111 221411 431111 111224 111422 121124 121421 141122 141221 112214 112412 122114 122411 142112 142211 241211 221114 413111 241112 134111 111242 121142 121241 114212 124112 124211 411212 421112 421211 212141 214121 412121 111143 111341 131141 114113 114311 411113 411311 113141 114131 311141 411131 211412 211214 211232 2331112")
            .split(" ");

    public static boolean[] modules(String value) {
        if (value == null || !value.matches("[0-9]{2,24}") || value.length() % 2 != 0)
            throw new IllegalArgumentException("Штрихкод должен содержать чётное количество цифр");
        List<Integer> symbols = new ArrayList<>();
        symbols.add(105);
        int checksum = 105;
        for (int i = 0; i < value.length(); i += 2) {
            int pair = Integer.parseInt(value.substring(i, i + 2));
            symbols.add(pair);
            checksum += pair * (i / 2 + 1);
        }
        symbols.add(checksum % 103);
        symbols.add(106);
        int size = 20 + symbols.stream().mapToInt(s -> PATTERNS[s].chars().map(c -> c - '0').sum()).sum();
        boolean[] bits = new boolean[size];
        int x = 10;
        for (int symbol : symbols) {
            boolean black = true;
            for (char width : PATTERNS[symbol].toCharArray()) {
                for (int j = 0; j < width - '0'; j++)
                    bits[x++] = black;
                black = !black;
            }
        }
        return bits;
    }

    public static String svg(String value) {
        boolean[] bits = modules(value);
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(bits.length)
                .append(" 65\" role=\"img\" aria-label=\"").append(value)
                .append("\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><g fill=\"black\" shape-rendering=\"crispEdges\">");
        for (int x = 0; x < bits.length; x++)
            if (bits[x])
                svg.append("<rect x=\"").append(x).append("\" y=\"0\" width=\"1\" height=\"48\"/>");
        return svg.append(
                "</g><text x=\"50%\" y=\"61\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"10\">")
                .append(value).append("</text></svg>").toString();
    }
}
