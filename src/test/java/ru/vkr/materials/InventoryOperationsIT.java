package ru.vkr.materials;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.service.*;
import ru.vkr.materials.web.form.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WithMockUser(roles = "STOREKEEPER")
class InventoryOperationsIT {
    @Autowired
    InventoryService operations;
    @Autowired
    InventoryReadService read;
    @Autowired
    MaterialRepository materials;
    @Autowired
    MaterialBatchRepository batches;
    @Autowired
    UnitRepository units;
    @Autowired
    CategoryRepository categories;
    @Autowired
    UserRepository users;
    @Autowired
    SupplierRepository suppliers;
    @Autowired
    WarehouseRepository warehouses;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    InventoryDocumentRepository documents;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;
    @Autowired
    WebApplicationContext context;
    Material material;
    User actor;
    Supplier supplier;
    Long warehouseId, departmentId;
    MockMvc mvc;

    @BeforeEach
    void fixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Unit unit = new Unit();
            unit.setName("Тестовая единица");
            unit.setSymbol("шт");
            units.save(unit);
            Category category = new Category();
            category.setName("Тест склада");
            categories.save(category);
            material = new Material();
            material.setCode("INV-" + suffix);
            material.setName("Материал теста склада");
            material.setUnit(unit);
            material.setCategory(category);
            material.setHasExpiry(true);
            materials.save(material);
            actor = new User();
            actor.setLogin("inventory-" + suffix);
            actor.setFullName("Кладовщик теста");
            actor.setPasswordHash("test-fixture-only");
            actor.setRole(UserRole.STOREKEEPER);
            users.save(actor);
            supplier = new Supplier();
            supplier.setName("Поставщик " + suffix);
            suppliers.save(supplier);
            warehouseId = warehouses.findAll().getFirst().getId();
            departmentId = departments.findAll().stream().filter(d -> d.getName().equals("ВЭЖХ")).findFirst()
                    .orElseThrow().getId();
        });
    }

    private InventoryForm receipt(String quantity) {
        InventoryForm f = base(DocumentType.RECEIPT);
        f.setSupplierId(supplier.getId());
        InventoryLineForm line = new InventoryLineForm();
        line.setMaterialId(material.getId());
        line.setBatchNumber("LOT-" + UUID.randomUUID().toString().substring(0, 8));
        line.setExpiresOn(LocalDate.now(clock).plusDays(30));
        line.setPurchaseUnitPrice(new BigDecimal("10.1234"));
        line.setQuantity(new BigDecimal(quantity));
        f.getLines().add(line);
        return f;
    }

    private InventoryForm base(DocumentType type) {
        InventoryForm f = new InventoryForm();
        f.setType(type);
        f.setDocumentDate(LocalDate.now(clock));
        f.setWarehouseId(warehouseId);
        return f;
    }

    private Long receiptBatch(Long documentId) {
        return jdbc.queryForObject(
                "SELECT material_batches_id FROM inventory_document_items WHERE inventory_documents_id=?", Long.class,
                documentId);
    }

    private InventoryForm expense(DocumentType type, Long batch, String quantity) {
        InventoryForm f = base(type);
        if (type == DocumentType.ISSUE)
            f.setDepartmentId(departmentId);
        else
            f.setWriteOffReason("Порча / просрочка");
        InventoryLineForm line = new InventoryLineForm();
        line.setBatchId(batch);
        line.setQuantity(new BigDecimal(quantity));
        f.getLines().add(line);
        return f;
    }

    private long version(Long id) {
        return documents.findById(id).orElseThrow().getVersion();
    }

    private void balance(Long batch, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(read.available(warehouseId, batch)));
    }

    private LaboratoryPrincipal principal() {
        return LaboratoryPrincipal.from(actor);
    }

    @Test
    void receiptDraftDoesNotAffectStockAndPostingIsRecordedExactlyOnce() {
        Long id = operations.create(receipt("10.5000"), actor.getId(), false);
        Long batch = receiptBatch(id);
        balance(batch, "0");
        operations.post(id, version(id), actor.getId());
        balance(batch, "10.5");
        assertEquals(DocumentStatus.POSTED, documents.findById(id).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM inventory_events WHERE inventory_documents_id=?",
                Integer.class, id));
        assertThrows(IllegalArgumentException.class, () -> operations.post(id, version(id), actor.getId()));
        balance(batch, "10.5");
        assertThrows(IllegalArgumentException.class, () -> operations.deleteDraft(id, version(id)));
    }

    @Test
    void issueAndWriteOffSubtractAndDraftCanBeDeleted() {
        Long receipt = operations.create(receipt("20"), actor.getId(), true);
        Long batch = receiptBatch(receipt);
        operations.create(expense(DocumentType.ISSUE, batch, "4.25"), actor.getId(), true);
        operations.create(expense(DocumentType.WRITE_OFF, batch, "1.5"), actor.getId(), true);
        balance(batch, "14.25");
        Long draft = operations.create(expense(DocumentType.ISSUE, batch, "5"), actor.getId(), false);
        operations.deleteDraft(draft, version(draft));
        assertFalse(documents.existsById(draft));
        balance(batch, "14.25");
    }

    @Test
    void insufficientAndRepeatedBatchLinesCannotProduceNegativeStock() {
        Long batch = receiptBatch(operations.create(receipt("10"), actor.getId(), true));
        long count = documents.count();
        assertThrows(IllegalArgumentException.class,
                () -> operations.create(expense(DocumentType.ISSUE, batch, "11"), actor.getId(), true));
        assertEquals(count, documents.count());
        balance(batch, "10");
        InventoryForm duplicate = expense(DocumentType.WRITE_OFF, batch, "6");
        InventoryLineForm line = new InventoryLineForm();
        line.setBatchId(batch);
        line.setQuantity(new BigDecimal("6"));
        duplicate.getLines().add(line);
        assertThrows(IllegalArgumentException.class, () -> operations.create(duplicate, actor.getId(), true));
        balance(batch, "10");
    }

    @Test
    void expiredBatchCannotBeIssuedButCanBeWrittenOff() {
        InventoryForm old = receipt("5");
        old.setDocumentDate(LocalDate.now(clock).minusDays(5));
        old.getLines().getFirst().setExpiresOn(LocalDate.now(clock).minusDays(1));
        Long batch = receiptBatch(operations.create(old, actor.getId(), true));
        assertThrows(IllegalArgumentException.class,
                () -> operations.create(expense(DocumentType.ISSUE, batch, "1"), actor.getId(), true));
        assertTrue(read.stocks(material.getCode()).getFirst().expired());
        operations.create(expense(DocumentType.WRITE_OFF, batch, "5"), actor.getId(), true);
        balance(batch, "0");
    }

    @Test
    void expiryTodayIsStillUsable() {
        InventoryForm today = receipt("2");
        today.getLines().getFirst().setExpiresOn(LocalDate.now(clock));
        Long batch = receiptBatch(operations.create(today, actor.getId(), true));
        operations.create(expense(DocumentType.ISSUE, batch, "2"), actor.getId(), true);
        balance(batch, "0");
    }

    @Test
    void invalidSecondReceiptLineRollsBackDocumentAndAllNewBatches() {
        long count = documents.count(), batchCount = batches.count();
        InventoryForm f = receipt("2");
        InventoryLineForm invalid = new InventoryLineForm();
        invalid.setMaterialId(material.getId());
        invalid.setBatchNumber("MISSING-EXPIRY");
        invalid.setQuantity(BigDecimal.ONE);
        f.getLines().add(invalid);
        assertThrows(IllegalArgumentException.class, () -> operations.create(f, actor.getId(), true));
        assertEquals(count, documents.count());
        assertEquals(batchCount, batches.count());
    }

    @Test
    void deletingReceiptDraftAlsoRemovesItsUnusedBatches() {
        Long id = operations.create(receipt("2"), actor.getId(), false);
        Long batch = receiptBatch(id);
        operations.deleteDraft(id, version(id));
        assertFalse(batches.existsById(batch));
        assertFalse(documents.existsById(id));
    }

    @Test
    void concurrentExpensesAllowOnlyOneWhenCombinedQuantityExceedsBalance() throws Exception {
        Long batch = receiptBatch(operations.create(receipt("10"), actor.getId(), true));
        Long a = operations.create(expense(DocumentType.ISSUE, batch, "7"), actor.getId(), false);
        Long b = operations.create(expense(DocumentType.ISSUE, batch, "7"), actor.getId(), false);
        long av = version(a), bv = version(b);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> postConcurrent(a, av, start));
            Future<Boolean> second = executor.submit(() -> postConcurrent(b, bv, start));
            start.countDown();
            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            balance(batch, "3");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean postConcurrent(Long id, long version, CountDownLatch start) throws Exception {
        var p = principal();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        try {
            start.await();
            operations.post(id, version, actor.getId());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void recipientSeesOnlyOwnPostedIssuesAndCannotAccessWarehouseOrOtherDocuments() throws Exception {
        Long receipt = operations.create(receipt("5"), actor.getId(), true);
        Long batch = receiptBatch(receipt);
        Long issue = operations.create(expense(DocumentType.ISSUE, batch, "1"), actor.getId(), true);
        Long otherDepartment = departments.findAll().stream().filter(d -> !d.getId().equals(departmentId)).findFirst()
                .orElseThrow().getId();
        InventoryForm other = expense(DocumentType.ISSUE, batch, "1");
        other.setDepartmentId(otherDepartment);
        Long otherIssue = operations.create(other, actor.getId(), true);
        User recipient = new User();
        recipient.setLogin("recipient-" + UUID.randomUUID());
        recipient.setFullName("Получатель");
        recipient.setRole(UserRole.RECIPIENT);
        recipient.setPasswordHash("test-only");
        recipient.setDepartment(departments.findById(departmentId).orElseThrow());
        users.saveAndFlush(recipient);
        var p = LaboratoryPrincipal.from(recipient);
        mvc.perform(get("/operations").with(user(p))).andExpect(status().isOk());
        mvc.perform(get("/operations/" + issue).with(user(p))).andExpect(status().isOk());
        mvc.perform(get("/operations/" + receipt).with(user(p))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/stock").with(user(p))).andExpect(status().isForbidden());
        mvc.perform(post("/inventory/documents/" + issue + "/post").with(user(p)).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/operations/" + otherIssue).with(user(p))).andExpect(status().isForbidden());
    }

    @Test
    void allOperationPagesRenderIncludingMultilineFormAndJournal() throws Exception {
        Long receipt = operations.create(receipt("5"), actor.getId(), true);
        for (String path : new String[] { "/inventory/stock", "/inventory/suppliers", "/inventory/suppliers/new",
                "/operations", "/operations/journal", "/operations/" + receipt,
                "/inventory/documents/new?type=RECEIPT", "/inventory/documents/new?type=ISSUE",
                "/inventory/documents/new?type=WRITE_OFF" }) {
            mvc.perform(get(path).with(user(principal()))).andExpect(status().isOk());
        }
        mvc.perform(post("/inventory/documents/" + receipt + "/post").with(user(principal())).param("version", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/inventory/documents/new?type=RECEIPT").with(user(principal())))
                .andExpect(content().string(containsString("lines[0].quantity")))
                .andExpect(content().string(containsString("lines[__index__].quantity")));
    }

    @Test
    void validationRejectsZeroAndMissingReasonAndFutureDate() {
        InventoryForm zero = receipt("0");
        assertThrows(IllegalArgumentException.class, () -> operations.create(zero, actor.getId(), true));
        InventoryForm future = receipt("1");
        future.setDocumentDate(LocalDate.now(clock).plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> operations.create(future, actor.getId(), true));
        Long batch = receiptBatch(operations.create(receipt("1"), actor.getId(), true));
        InventoryForm blank = expense(DocumentType.WRITE_OFF, batch, "1");
        blank.setWriteOffReason("");
        assertThrows(IllegalArgumentException.class, () -> operations.create(blank, actor.getId(), true));
    }

    @Test
    void issueCannotPredateItsReceiptAndDraftStaleVersionIsRejected() {
        Long batch = receiptBatch(operations.create(receipt("5"), actor.getId(), true));
        InventoryForm early = expense(DocumentType.ISSUE, batch, "1");
        early.setDocumentDate(LocalDate.now(clock).minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> operations.create(early, actor.getId(), true));
        Long draft = operations.create(expense(DocumentType.ISSUE, batch, "1"), actor.getId(), false);
        assertThrows(IllegalArgumentException.class, () -> operations.post(draft, version(draft) + 1, actor.getId()));
        balance(batch, "5");
    }
}
