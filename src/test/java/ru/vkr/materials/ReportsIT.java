package ru.vkr.materials;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.service.*;
import ru.vkr.materials.web.form.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WithMockUser(roles = "STOREKEEPER")
class ReportsIT {
    @Autowired
    ReportService reports;
    @Autowired
    InventoryService operations;
    @Autowired
    InventoryReadService inventory;
    @Autowired
    UserRepository users;
    @Autowired
    MaterialRepository materials;
    @Autowired
    MaterialBatchRepository batches;
    @Autowired
    UnitRepository units;
    @Autowired
    CategoryRepository categories;
    @Autowired
    SupplierRepository suppliers;
    @Autowired
    WarehouseRepository warehouses;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    InventoryDocumentRepository documents;
    @Autowired
    Clock clock;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    WebApplicationContext context;
    Material material;
    User actor;
    Supplier supplier;
    Long warehouseId, departmentId, otherDepartmentId;
    MockMvc mvc;
    LocalDate today;

    @BeforeEach
    void fixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        today = LocalDate.now(clock);
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Unit u = new Unit();
            u.setName("Штука");
            u.setSymbol("шт");
            units.save(u);
            Category c = new Category();
            c.setName("Тест отчёта");
            categories.save(c);
            material = new Material();
            material.setCode("REP-" + suffix);
            material.setName("Материал отчёта");
            material.setUnit(u);
            material.setCategory(c);
            material.setHasExpiry(true);
            materials.save(material);
            actor = new User();
            actor.setLogin("report-" + suffix);
            actor.setFullName("Кладовщик отчётов");
            actor.setPasswordHash("fixture-only");
            actor.setRole(UserRole.STOREKEEPER);
            users.save(actor);
            supplier = new Supplier();
            supplier.setName("Поставщик отчётов");
            suppliers.save(supplier);
            warehouseId = warehouses.findAll().getFirst().getId();
            var ds = departments.findAll();
            departmentId = ds.stream().filter(d -> d.getName().equals("ВЭЖХ")).findFirst().orElseThrow().getId();
            otherDepartmentId = ds.stream().filter(d -> !d.getId().equals(departmentId)).findFirst().orElseThrow()
                    .getId();
        });
    }

    private InventoryForm base(DocumentType type, LocalDate date) {
        InventoryForm f = new InventoryForm();
        f.setType(type);
        f.setDocumentDate(date);
        f.setWarehouseId(warehouseId);
        return f;
    }

    private Long receipt(String qty, LocalDate date, LocalDate expiry, boolean post) {
        InventoryForm f = base(DocumentType.RECEIPT, date);
        f.setSupplierId(supplier.getId());
        InventoryLineForm l = new InventoryLineForm();
        l.setMaterialId(material.getId());
        l.setBatchNumber("LOT-" + UUID.randomUUID().toString().substring(0, 8));
        l.setExpiresOn(expiry);
        l.setQuantity(new BigDecimal(qty));
        f.getLines().add(l);
        return operations.create(f, actor.getId(), post);
    }

    private Long batch(Long doc) {
        return new TransactionTemplate(transactions)
                .execute(s -> documents.findById(doc).orElseThrow().getItems().getFirst().getBatch().getId());
    }

    private Long expense(DocumentType type, Long batch, String qty, LocalDate date, Long department) {
        InventoryForm f = base(type, date);
        if (type == DocumentType.ISSUE)
            f.setDepartmentId(department);
        else
            f.setWriteOffReason("Списание теста");
        InventoryLineForm l = new InventoryLineForm();
        l.setBatchId(batch);
        l.setQuantity(new BigDecimal(qty));
        f.getLines().add(l);
        return operations.create(f, actor.getId(), true);
    }

    private LaboratoryPrincipal actorPrincipal() {
        return LaboratoryPrincipal.from(actor);
    }

    private User recipient() {
        User u = new User();
        u.setLogin("report-recipient-" + UUID.randomUUID());
        u.setFullName("Получатель отчёта");
        u.setRole(UserRole.RECIPIENT);
        u.setPasswordHash("fixture-only");
        u.setDepartment(departments.findById(departmentId).orElseThrow());
        return users.saveAndFlush(u);
    }

    private void number(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }

    @Test
    void stockAsOfAndMovementBalanceMatchPostedOperations() {
        Long b = batch(receipt("10", today.minusDays(4), today.plusDays(10), true));
        expense(DocumentType.ISSUE, b, "3", today.minusDays(2), departmentId);
        expense(DocumentType.WRITE_OFF, b, "2", today.minusDays(1), null);
        receipt("100", today, today.plusDays(20), false);
        var historical = reports.stocks(today.minusDays(3), warehouseId, material.getCode());
        assertEquals(1, historical.rows().size());
        number("10", historical.rows().getFirst().get(5));
        var current = reports.stocks(today, warehouseId, material.getCode());
        number("5", current.rows().getFirst().get(5));
        var movement = reports.movement(today.minusDays(2), today, warehouseId, material.getCode());
        var row = movement.rows().getFirst();
        number("10", row.get(4));
        number("0", row.get(5));
        number("3", row.get(6));
        number("2", row.get(7));
        number("5", row.get(8));
    }

    @Test
    void expiryIncludesTodayAndThresholdButExcludesLaterAndEmptyBatches() {
        receipt("1", today.minusDays(5), today.minusDays(1), true);
        receipt("1", today, today, true);
        receipt("1", today, today.plusDays(30), true);
        receipt("1", today, today.plusDays(31), true);
        Long empty = batch(receipt("1", today, today.plusDays(2), true));
        expense(DocumentType.WRITE_OFF, empty, "1", today, null);
        assertEquals(3, reports.expiry(30, "all", warehouseId, material.getCode()).rows().size());
        assertEquals(1, reports.expiry(30, "expired", warehouseId, material.getCode()).rows().size());
        assertEquals(2, reports.expiry(30, "soon", warehouseId, material.getCode()).rows().size());
        assertEquals(1, reports.expiry(1, "soon", warehouseId, material.getCode()).rows().size());
    }

    @Test
    void expiryExcludesMaterialsWithoutExpiryDate() {
        material.setHasExpiry(false);
        materials.saveAndFlush(material);
        receipt("5", today, null, true);
        assertTrue(reports.expiry(30, "all", warehouseId, material.getCode()).rows().isEmpty());
        assertEquals(1, reports.stocks(today, warehouseId, material.getCode()).rows().size());
    }

    @Test
    void movementIncludesExactBoundaryDatesAndKeepsZeroClosingBalanceWithActivity() {
        Long b = batch(receipt("10", today.minusDays(2), today.plusDays(30), true));
        expense(DocumentType.ISSUE, b, "10", today, departmentId);
        var table = reports.movement(today.minusDays(2), today, warehouseId, material.getCode());
        var r = table.rows().getFirst();
        number("0", r.get(4));
        number("10", r.get(5));
        number("10", r.get(6));
        number("0", r.get(8));
        assertTrue(reports.stocks(today, warehouseId, material.getCode()).rows().isEmpty());
    }

    @Test
    void departmentIssuesAggregateByMaterialAndRespectPeriod() {
        Long b = batch(receipt("20", today.minusDays(4), today.plusDays(20), true));
        expense(DocumentType.ISSUE, b, "2", today.minusDays(3), departmentId);
        expense(DocumentType.ISSUE, b, "3", today, departmentId);
        expense(DocumentType.ISSUE, b, "4", today, otherDepartmentId);
        var table = reports.issues(actorPrincipal(), today.minusDays(1), today, warehouseId, departmentId,
                material.getCode());
        assertEquals(1, table.rows().size());
        number("3", table.rows().getFirst().get(5));
    }

    @Test
    void reportPagesCsvLinksAndPrintControlsRender() throws Exception {
        receipt("5", today, today.plusDays(5), true);
        for (String kind : List.of("expiry", "stocks", "movement", "issues")) {
            String html = mvc
                    .perform(get("/reports/" + kind).with(user(actorPrincipal())).param("q", material.getCode()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("data-print")))
                    .andExpect(content().string(containsString("/reports/" + kind + ".csv"))).andReturn().getResponse()
                    .getContentAsString();
            if (kind.equals("stocks"))
                java.nio.file.Files.writeString(java.nio.file.Path.of("target/report-preview.html"), html);
            mvc.perform(get("/reports/" + kind + ".csv").with(user(actorPrincipal())).param("q", material.getCode()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(content().contentTypeCompatibleWith("text/csv"));
        }
    }

    @Test
    void recipientCannotExportWarehouseReportsOrAnotherDepartmentsIssues() throws Exception {
        Long receipt = receipt("10", today, today.plusDays(30), true), b = batch(receipt);
        Long own = expense(DocumentType.ISSUE, b, "2", today, departmentId),
                other = expense(DocumentType.ISSUE, b, "3", today, otherDepartmentId);
        var p = LaboratoryPrincipal.from(recipient());
        for (String path : List.of("/reports/stocks", "/reports/stocks.csv", "/reports/movement",
                "/reports/movement.csv", "/reports/expiry", "/reports/expiry.csv"))
            mvc.perform(get(path).with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/reports/issues.csv").with(user(p)).param("departmentId", otherDepartmentId.toString()))
                .andExpect(status().isForbidden());
        var result = mvc.perform(get("/reports/issues.csv").with(user(p)).param("q", material.getCode()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(result.contains("\"2\""));
        assertFalse(result.contains("\"3\""));
        mvc.perform(get("/operations/" + own + "/export.csv").with(user(p))).andExpect(status().isOk());
        mvc.perform(get("/operations/" + other + "/export.csv").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/operations/" + receipt + "/export.csv").with(user(p))).andExpect(status().isForbidden());
    }

    @Test
    void invalidDatesModesAndThresholdsProduceReadableErrors() throws Exception {
        mvc.perform(get("/reports/movement").with(user(actorPrincipal())).param("from", today.toString()).param("to",
                today.minusDays(1).toString()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Проверьте параметры")));
        mvc.perform(get("/reports/expiry.csv").with(user(actorPrincipal())).param("days", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/reports/expiry").with(user(actorPrincipal())).param("mode", "bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/reports/stocks").with(user(actorPrincipal())).param("date", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentCsvIncludesDraftStatusAndPrintButtonIsAvailable() throws Exception {
        Long id = receipt("2.5000", today, today.plusDays(2), false);
        mvc.perform(get("/operations/" + id).with(user(actorPrincipal()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Печать / PDF")));
        var csv = mvc.perform(get("/operations/" + id + "/export.csv").with(user(actorPrincipal())))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Черновик"));
        assertTrue(csv.contains("2,5"));
    }

    @Test
    void csvExportUsesTheSameSearchFilterAsTheReportPage() throws Exception {
        receipt("3", today, today.plusDays(2), true);
        String found = mvc
                .perform(get("/reports/stocks.csv").with(user(actorPrincipal())).param("q", material.getCode()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(found.contains(material.getCode()));
        String empty = mvc
                .perform(get("/reports/stocks.csv").with(user(actorPrincipal())).param("q",
                        "NONEXISTENT-" + UUID.randomUUID()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(empty.contains(material.getCode()));
        assertEquals(1, empty.lines().count());
    }

    @Test
    void batchLabelsStockSearchAndScannerOptionsUseSameStableCode() throws Exception {
        Long doc = receipt("10", today, today.plusDays(30), true);
        Long b = batch(doc);
        String code = batches.findById(b).orElseThrow().getBatchNumber();
        assertEquals(b, inventory.stocks(code).getFirst().batchId());
        assertEquals(0, inventory.available(warehouseId, b).compareTo(new BigDecimal("10")));
        mvc.perform(get("/inventory/stock").param("q", code)).andExpect(status().isOk())
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("/inventory/batches/" + b + "/label")));
        mvc.perform(get("/inventory/documents/new").param("type", "ISSUE")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-barcode=\"" + code + "\"")))
                .andExpect(content().string(containsString("batch-scan")));
        var labelPage = mvc.perform(get("/inventory/batches/" + b + "/label")).andExpect(status().isOk())
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("QZ Tray"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/label-page-preview.html"),
                labelPage.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/inventory/batches/" + b + "/label.png")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mvc.perform(get("/inventory/batches/" + b + "/label-job").param("copies", "2").param("gap", "2.5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNotEmpty());
        mvc.perform(get("/inventory/batches/" + b + "/label-job").param("copies", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/inventory/batches/" + b + "/label-job").param("gap", "NaN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/inventory/batches/9223372036854775807/label")).andExpect(status().isNotFound());
    }

    @Test
    void documentBarcodeIsIncludedInPrintAndSearchAndChecksRecipientScope() throws Exception {
        Long receipt = receipt("10", today, today.plusDays(30), true);
        Long b = batch(receipt);
        Long issue = expense(DocumentType.ISSUE, b, "1", today, departmentId);
        Long otherIssue = expense(DocumentType.ISSUE, b, "1", today, otherDepartmentId);
        String code = Barcode.document(issue);
        assertEquals(issue, inventory.documents(actorPrincipal(), code, "").getFirst().id());
        var page = mvc.perform(get("/operations/" + issue).with(user(actorPrincipal()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/operations/" + issue + "/barcode.svg"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/document-barcode-preview.html"),
                page.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/operations/" + issue + "/barcode.svg").with(user(actorPrincipal())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString(code)));
        var p = LaboratoryPrincipal.from(recipient());
        mvc.perform(get("/operations/" + issue + "/barcode.svg").with(user(p))).andExpect(status().isOk());
        mvc.perform(get("/operations/" + receipt + "/barcode.svg").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/operations/" + otherIssue + "/barcode.svg").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/batches/" + b + "/label").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/batches/" + b + "/label.png").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/batches/" + b + "/label-job").with(user(p))).andExpect(status().isForbidden());
    }
}
