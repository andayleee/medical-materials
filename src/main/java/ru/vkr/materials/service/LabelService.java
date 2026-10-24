package ru.vkr.materials.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
public class LabelService {
    public record Label(long id, String name, String materialCode, String batch, LocalDate expiresOn, String barcode) {
    }

    private final JdbcTemplate jdbc;

    public LabelService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Label label(long id) {
        var rows = jdbc.query("""
                SELECT b.id_material_batches,m.name,m.code,b.batch_number,b.expires_on
                FROM material_batches b JOIN materials m ON m.id_materials=b.materials_id
                WHERE b.id_material_batches=?
                """, (rs, n) -> new Label(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, LocalDate.class), rs.getString(4)), id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Партия не найдена");
        return rows.getFirst();
    }

    public byte[] preview(long id) {
        return png(label(id));
    }

    public String job(long id, int copies, double gap) {
        if (copies < 1 || copies > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Допустимо от 1 до 100 этикеток");
        if (!Double.isFinite(gap) || gap < 0 || gap > 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Зазор должен быть от 0 до 10 мм");
        return java.util.Base64.getEncoder().encodeToString(tspl(label(id), copies, gap));
    }

    static BufferedImage header(Label label) {
        BufferedImage image = new BufferedImage(320, 88, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 320, 88);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        String text = label.name().replaceAll("[\\p{Cntrl}]", " ");
        int split = text.length();
        while (split > 0 && g.getFontMetrics().stringWidth(text.substring(0, split)) > 296)
            split--;
        if (split < text.length()) {
            int space = text.lastIndexOf(' ', split);
            if (space > split / 2)
                split = space;
        }
        g.drawString(text.substring(0, split), 12, 18);
        if (split < text.length())
            g.drawString(fit(g, text.substring(split).strip(), 296), 12, 36);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(fit(g, "Партия: " + label.batch().replaceAll("[\\p{Cntrl}]", " "), 296), 12, 55);
        g.drawString("Годен до: " + (label.expiresOn() == null ? "не учитывается"
                : label.expiresOn().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))), 12, 75);
        g.dispose();
        return image;
    }

    private static String fit(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width)
            return text;
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "…") > width)
            end--;
        return text.substring(0, end) + "…";
    }

    public static byte[] png(Label label) {
        try {
            BufferedImage image = new BufferedImage(320, 160, BufferedImage.TYPE_BYTE_BINARY);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 320, 160);
            g.drawImage(header(label), 0, 0, null);
            boolean[] bits = Code128.modules(label.barcode());
            int left = (320 - bits.length * 2) / 2;
            g.setColor(Color.BLACK);
            for (int x = 0; x < bits.length; x++)
                if (bits[x])
                    g.fillRect(left + x * 2, 90, 2, 40);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            int width = g.getFontMetrics().stringWidth(label.barcode());
            g.drawString(label.barcode(), (320 - width) / 2, 151);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static byte[] tspl(Label label, int copies, double gap) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(
                    ("SIZE 40 mm,20 mm\r\nGAP " + java.math.BigDecimal.valueOf(gap).stripTrailingZeros().toPlainString()
                            + " mm,0 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nCLS\r\nBITMAP 0,0,40,88,0,")
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            BufferedImage image = header(label);
            for (int y = 0; y < 88; y++)
                for (int block = 0; block < 40; block++) {
                    int value = 0;
                    for (int bit = 0; bit < 8; bit++)
                        if ((image.getRGB(block * 8 + bit, y) & 0xffffff) != 0)
                            value |= 1 << (7 - bit);
                    out.write(value);
                }
            int x = (320 - Code128.modules(label.barcode()).length * 2) / 2 + 20;
            out.write(("\r\nBARCODE " + x + ",90,\"128\",40,1,0,2,2,\"" + label.barcode() + "\"\r\nPRINT 1," + copies
                    + "\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
