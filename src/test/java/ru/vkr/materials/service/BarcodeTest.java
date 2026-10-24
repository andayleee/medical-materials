package ru.vkr.materials.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarcodeTest {
    @Test
    void identifiersAreStableAndSeparateBatchesFromDocuments() {
        assertEquals("20000000000123", Barcode.document(123));
        assertEquals(123L, Barcode.documentId(Barcode.document(123)));
        assertTrue(Barcode.isBatchNumber("024102026500"));
        assertTrue(Barcode.isBatchNumber("999999999999"));
        assertFalse(Barcode.isBatchNumber("000000000000"));
        assertFalse(Barcode.isBatchNumber(Barcode.document(123)));
        assertNull(Barcode.documentId("024102026500"));
        assertThrows(IllegalArgumentException.class, () -> Barcode.document(0));
        assertThrows(IllegalArgumentException.class, () -> Barcode.document(1000000000000L));
    }

    @Test
    void code128CMatchesKnownSymbolsAndWeightedChecksum() {
        String[] patterns = { "211232", "112232", "131123", "331121", "132131", "2331112" };
        StringBuilder expected = new StringBuilder("0".repeat(10));
        for (String p : patterns) {
            boolean bar = true;
            for (char width : p.toCharArray()) {
                expected.append((bar ? "1" : "0").repeat(width - '0'));
                bar = !bar;
            }
        }
        expected.append("0".repeat(10));
        boolean[] bits = Code128.modules("123456");
        StringBuilder actual = new StringBuilder();
        for (boolean bit : bits)
            actual.append(bit ? '1' : '0');
        assertEquals(expected.toString(), actual.toString());
        assertThrows(IllegalArgumentException.class, () -> Code128.modules("123"));
        assertThrows(IllegalArgumentException.class, () -> Code128.modules("<script>"));
    }

    @Test
    void batchAndDocumentNumbersFitWithQuietZones() {
        assertTrue(Code128.modules("999999999999").length * 2 <= 296);
        String svg = Code128.svg(Barcode.document(1));
        assertTrue(svg.contains("20000000000001"));
        assertTrue(svg.contains("viewBox"));
        assertFalse(svg.contains("<script"));
    }
}
