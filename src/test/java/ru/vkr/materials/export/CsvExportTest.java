package ru.vkr.materials.export;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.vkr.materials.service.ReportTable;
import static org.junit.jupiter.api.Assertions.*;

class CsvExportTest {
    private String csv(Object... values) {
        return new String(CsvExport.encode(new ReportTable("Тест","",List.of("Поле"),List.of(ReportTable.row(values)))),StandardCharsets.UTF_8);
    }
    @Test void includesUtf8BomAndPreservesRussianText(){assertTrue(csv("Реактив").startsWith("\uFEFF"));assertTrue(csv("Реактив").contains("Реактив"));}
    @Test void escapesQuotesDelimitersAndNewlines(){assertTrue(csv("Раствор; \"А\"\nБ").contains("\"Раствор; \"\"А\"\"\nБ\""));}
    @Test void neutralizesSpreadsheetFormulasInText(){for(String value:List.of("=1+1","+cmd","-cmd","@SUM(1)","   =1+1","\t=1+1"))assertTrue(csv(value).contains("\"'"+value+"\""));}
    @Test void keepsTypedNumbersNumericAndUsesDecimalComma(){assertTrue(csv(new BigDecimal("-2.5000")).contains("\"-2,5\""));assertFalse(csv(new BigDecimal("-2.5000")).contains("'-2"));}
}
