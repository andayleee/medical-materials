package ru.vkr.materials.service;

import java.io.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabelRenderingTest {
    @org.junit.jupiter.api.BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private LabelService.Label label() {
        return new LabelService.Label(123, "Пробирка с ЭДТА, 4 мл", "BM-038", "024102026500", LocalDate.of(2027, 9, 30),
                "024102026500");
    }

    @Test
    void previewHasPhysicalPixelDimensionsAndReadableBarcodeQuietZones() throws Exception {
        byte[] png = LabelService.png(label());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(320, image.getWidth());
        assertEquals(160, image.getHeight());
        boolean[] bits = Code128.modules(label().barcode());
        int left = (320 - bits.length * 2) / 2;
        for (int x = 0; x < bits.length; x++)
            assertEquals(bits[x] ? 0 : 0xffffff, image.getRGB(left + x * 2, 110) & 0xffffff);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/label-preview.png"), png);
    }

    @Test
    void tsplPreservesBitmapBytesAndPrintsRequestedCopiesWithNativeBarcode() {
        byte[] bytes = LabelService.tspl(label(), 3, 2.5);
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        String marker = "BITMAP 0,0,40,88,0,";
        int offset = raw.indexOf(marker) + marker.length();
        assertTrue(raw.startsWith("SIZE 40 mm,20 mm\r\nGAP 2.5 mm,0 mm"));
        assertEquals(255, Byte.toUnsignedInt(bytes[offset]));
        String tail = new String(bytes, offset + 40 * 88, bytes.length - offset - 40 * 88, StandardCharsets.US_ASCII);
        assertTrue(tail.contains("\"128\",40,1,0,2,2,\"024102026500\""));
        assertTrue(tail.endsWith("PRINT 1,3\r\n"));
        assertEquals(label().batch(), label().barcode());
    }

    @Test
    void nullExpiryAndLongOrControlTextDoNotBreakLabelCommands() {
        var label = new LabelService.Label(1, "Очень длинное название ".repeat(20), "X", "\"\r\nPRINT 1,100", null,
                "000000000001");
        assertDoesNotThrow(() -> LabelService.png(label));
        String raw = new String(LabelService.tspl(label, 1, 0), StandardCharsets.ISO_8859_1);
        assertTrue(raw.endsWith("PRINT 1,1\r\n"));
        assertFalse(raw.contains("PRINT 1,100"));
    }
}
