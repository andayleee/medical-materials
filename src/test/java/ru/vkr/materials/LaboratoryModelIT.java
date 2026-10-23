package ru.vkr.materials;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.InventoryDocumentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class LaboratoryModelIT {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryDocumentRepository documents;

    @Test
    void migratesExistingMaterialsAndPreservesTheirIdentifiersAndUnits() {
        Material legacy = em.createQuery("select m from Material m where m.code = :code", Material.class)
                .setParameter("code", "LEGACY-MIGRATION-CHECK").getSingleResult();
        assertEquals(501L, legacy.getId());
        assertEquals("Тестовый материал до миграции", legacy.getName());
        assertEquals("упак", legacy.getUnit().getSymbol());
        assertEquals("Без категории", legacy.getCategory().getName());
        assertFalse(legacy.isHasExpiry());
        assertTrue(legacy.isActive());
    }

    @Test
    void savesAndLoadsReceiptAndIssueUsingTheSameBatchAndDifferentDocuments() {
        Unit unit = new Unit(); unit.setName("Штука"); unit.setSymbol("шт"); em.persist(unit);
        Category category = new Category(); category.setName("Расходные материалы"); em.persist(category);
        Material material = new Material(); material.setCode("T-001"); material.setName("Пробирка");
        material.setUnit(unit); material.setCategory(category); material.setHasExpiry(true); em.persist(material);
        MaterialBatch batch = new MaterialBatch(); batch.setMaterial(material); batch.setBatchNumber("LOT-001");
        batch.setExpiresOn(LocalDate.of(2028, 12, 31)); batch.setPurchaseUnitPrice(new BigDecimal("12.3456")); em.persist(batch);
        Supplier supplier = new Supplier(); supplier.setName("Поставщик"); em.persist(supplier);
        Warehouse warehouse = em.createQuery("from Warehouse", Warehouse.class).getSingleResult();
        Department department = em.createQuery("from Department where name = 'ВЭЖХ'", Department.class).getSingleResult();
        assertEquals("Производство", department.getParent().getName());
        User author = new User(); author.setLogin("warehouse-test"); author.setFullName("Сотрудник склада");
        author.setPasswordHash("test-fixture-only-not-a-real-password"); author.setRole(UserRole.STOREKEEPER); em.persist(author);

        InventoryDocument receipt = document(warehouse, author, DocumentType.RECEIPT, "IN-001");
        receipt.setSupplier(supplier); receipt.addItem(item(batch, "100.5000")); documents.saveAndFlush(receipt);
        InventoryDocument issue = document(warehouse, author, DocumentType.ISSUE, "OUT-001");
        issue.setRecipientDepartment(department); issue.addItem(item(batch, "20.2500")); documents.saveAndFlush(issue);
        Long receiptId = receipt.getId(), issueId = issue.getId(); em.clear();

        InventoryDocument savedReceipt = documents.findById(receiptId).orElseThrow();
        InventoryDocument savedIssue = documents.findById(issueId).orElseThrow();
        assertNull(savedReceipt.getRecipientDepartment());
        assertNull(savedIssue.getSupplier());
        assertNull(savedIssue.getAuthor().getDepartment());
        assertEquals("ВЭЖХ", savedIssue.getRecipientDepartment().getName());
        assertEquals(savedReceipt.getItems().getFirst().getBatch().getId(), savedIssue.getItems().getFirst().getBatch().getId());
        assertEquals(new BigDecimal("20.2500"), savedIssue.getItems().getFirst().getQuantity());
        assertEquals(new BigDecimal("12.3456"), savedIssue.getItems().getFirst().getBatch().getPurchaseUnitPrice());
        assertEquals(DocumentStatus.DRAFT, savedIssue.getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from departments where name = 'Склад'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'warehouses' and column_name = 'departments_id'", Integer.class));
    }

    @Test
    void databaseRejectsIssueWithoutRecipient() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into inventory_documents (warehouses_id, users_id, number, type, status, document_date)
            values ((select id_warehouses from warehouses limit 1), ?, 'INVALID', 'ISSUE', 'DRAFT', current_date)
            """, persistAuthor()));
    }

    @Test
    void databaseRejectsNegativePurchasePrice() {
        Long materialId = jdbc.queryForObject("select id_materials from materials where code = 'LEGACY-MIGRATION-CHECK'", Long.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into material_batches (materials_id, batch_number, purchase_unit_price) values (?, 'BAD', -1)
            """, materialId));
    }

    private Long persistAuthor() {
        User user = new User(); user.setLogin("invalid-document-author"); user.setFullName("Тест");
        user.setPasswordHash("test-fixture"); user.setRole(UserRole.STOREKEEPER); em.persist(user); em.flush();
        return user.getId();
    }

    private InventoryDocument document(Warehouse warehouse, User author, DocumentType type, String number) {
        InventoryDocument document = new InventoryDocument(); document.setWarehouse(warehouse); document.setAuthor(author);
        document.setType(type); document.setNumber(number); document.setDocumentDate(LocalDate.now()); return document;
    }

    private InventoryDocumentItem item(MaterialBatch batch, String quantity) {
        InventoryDocumentItem item = new InventoryDocumentItem(); item.setBatch(batch); item.setLineNumber(1);
        item.setQuantity(new BigDecimal(quantity)); return item;
    }
}
