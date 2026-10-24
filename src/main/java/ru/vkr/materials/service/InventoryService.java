package ru.vkr.materials.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.web.form.*;

@Service
@Transactional
@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
public class InventoryService {
    private final InventoryDocumentRepository documents;
    private final WarehouseRepository warehouses;
    private final MaterialRepository materials;
    private final MaterialBatchRepository batches;
    private final SupplierRepository suppliers;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final InventoryReadService stock;
    private final EntityManager em;
    private final Clock clock;

    public InventoryService(InventoryDocumentRepository documents, WarehouseRepository warehouses,
            MaterialRepository materials,
            MaterialBatchRepository batches, SupplierRepository suppliers, DepartmentRepository departments,
            UserRepository users,
            InventoryReadService stock, EntityManager em, Clock clock) {
        this.documents = documents;
        this.warehouses = warehouses;
        this.materials = materials;
        this.batches = batches;
        this.suppliers = suppliers;
        this.departments = departments;
        this.users = users;
        this.stock = stock;
        this.em = em;
        this.clock = clock;
    }

    public Long create(InventoryForm f, Long actorId, boolean post) {
        if (f.getType() == null || f.getWarehouseId() == null || f.getDocumentDate() == null)
            fail("Укажите тип, склад и дату.");
        if (f.getDocumentDate().isAfter(LocalDate.now(clock)))
            fail("Дата документа не может быть в будущем.");
        if (f.getLines() == null || f.getLines().isEmpty() || f.getLines().size() > 100)
            fail("Документ должен содержать от 1 до 100 строк.");
        Warehouse warehouse = warehouses.findLockedById(f.getWarehouseId())
                .orElseThrow(() -> invalid("Склад не найден."));
        if (!warehouse.isActive())
            fail("Склад отключён.");
        InventoryDocument d = new InventoryDocument();
        d.setWarehouse(warehouse);
        d.setType(f.getType());
        d.setDocumentDate(f.getDocumentDate());
        d.setAuthor(actor(actorId));
        d.setNumber(f.getNumber() == null || f.getNumber().isBlank() ? UUID.randomUUID().toString()
                : f.getNumber().strip());
        if (d.getNumber().length() > 64)
            fail("Номер: не более 64 символов.");
        switch (f.getType()) {
            case RECEIPT -> {
                if (f.getSupplierId() == null || f.getDepartmentId() != null)
                    fail("Для поступления укажите только поставщика.");
                Supplier supplier = suppliers.findById(f.getSupplierId())
                        .orElseThrow(() -> invalid("Поставщик не найден."));
                if (!supplier.isActive())
                    fail("Поставщик отключён.");
                d.setSupplier(supplier);
            }
            case ISSUE -> {
                if (f.getDepartmentId() == null || f.getSupplierId() != null)
                    fail("Для выдачи укажите только подразделение-получателя.");
                Department department = departments.findById(f.getDepartmentId())
                        .orElseThrow(() -> invalid("Подразделение не найдено."));
                if (!department.isActive())
                    fail("Подразделение отключено.");
                d.setRecipientDepartment(department);
            }
            case WRITE_OFF -> {
                if (f.getDepartmentId() != null || f.getSupplierId() != null || f.getWriteOffReason() == null
                        || f.getWriteOffReason().isBlank())
                    fail("Укажите причину списания, без поставщика и подразделения.");
                if (f.getWriteOffReason().length() > 1000)
                    fail("Причина: не более 1000 символов.");
                d.setWriteOffReason(f.getWriteOffReason().strip());
            }
        }
        int line = 0;
        if (f.getType() == DocumentType.RECEIPT) {
            List<Material> lockedMaterials = f.getLines().stream().filter(Objects::nonNull)
                    .map(InventoryLineForm::getMaterialId).filter(Objects::nonNull).distinct().sorted()
                    .map(id -> materials.findLockedById(id).orElseThrow(() -> invalid("Материал не найден."))).toList();
            lockedMaterials.stream().map(m -> m.getUnit().getId()).distinct().sorted()
                    .forEach(id -> em.find(Unit.class, id, LockModeType.PESSIMISTIC_WRITE));
        }
        for (InventoryLineForm input : f.getLines()) {
            if (input == null)
                fail("Пустая строка документа.");
            decimal(input.getQuantity(), false, "Количество");
            InventoryDocumentItem item = new InventoryDocumentItem();
            item.setLineNumber(++line);
            item.setQuantity(input.getQuantity());
            if (f.getType() == DocumentType.RECEIPT) {
                if (input.getMaterialId() == null)
                    fail("Выберите материал в каждой строке.");
                Material m = materials.findLockedById(input.getMaterialId())
                        .orElseThrow(() -> invalid("Материал не найден."));
                if (!m.isActive())
                    fail("Нельзя принимать архивный материал.");
                if (input.getBatchNumber() != null && input.getBatchNumber().length() > 100)
                    fail("Номер партии производителя: не более 100 символов.");
                if (m.isHasExpiry() && input.getExpiresOn() == null)
                    fail("Для материала «" + m.getName() + "» нужен срок годности.");
                if (input.getExpiresOn() != null && input.getExpiresOn().isBefore(f.getDocumentDate()))
                    fail("Срок годности партии раньше даты поступления.");
                if (input.getPurchaseUnitPrice() != null)
                    decimal(input.getPurchaseUnitPrice(), true, "Цена");
                MaterialBatch b = new MaterialBatch();
                b.setMaterial(m);
                b.setSupplierBatchNumber(input.getBatchNumber() == null || input.getBatchNumber().isBlank() ? null
                        : input.getBatchNumber().strip());
                b.setExpiresOn(input.getExpiresOn());
                b.setPurchaseUnitPrice(input.getPurchaseUnitPrice());
                batches.save(b);
                item.setBatch(b);
            } else {
                if (input.getBatchId() == null)
                    fail("Выберите партию.");
                item.setBatch(batches.findById(input.getBatchId()).orElseThrow(() -> invalid("Партия не найдена.")));
            }
            d.addItem(item);
        }
        documents.saveAndFlush(d);
        if (f.getNumber() == null || f.getNumber().isBlank()) {
            String prefix = switch (d.getType()) {
                case RECEIPT -> "ПР-";
                case ISSUE -> "ВЫД-";
                case WRITE_OFF -> "СП-";
            };
            d.setNumber(prefix + String.format("%06d", d.getId()));
            documents.flush();
        }
        if (post)
            postLocked(d, actorId);
        return d.getId();
    }

    public void post(Long id, long expectedVersion, Long actorId) {
        InventoryDocument d = locked(id);
        checkDraft(d, expectedVersion);
        postLocked(d, actorId);
    }

    public void deleteDraft(Long id, long expectedVersion) {
        InventoryDocument d = locked(id);
        checkDraft(d, expectedVersion);
        List<MaterialBatch> receiptBatches = d.getType() == DocumentType.RECEIPT
                ? d.getItems().stream().map(InventoryDocumentItem::getBatch).toList()
                : List.of();
        List<InventoryDocumentItem> oldItems = List.copyOf(d.getItems());
        d.clearItems();
        oldItems.forEach(em::remove);
        em.flush();
        em.remove(d);
        em.flush();
        for (MaterialBatch b : receiptBatches) {
            Long refs = em.createQuery("select count(i) from InventoryDocumentItem i where i.batch.id=:id", Long.class)
                    .setParameter("id", b.getId()).getSingleResult();
            if (refs == 0)
                em.remove(b);
        }
        em.flush();
    }

    private InventoryDocument locked(Long id) {
        Long warehouse = documents.warehouseId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        warehouses.findLockedById(warehouse).orElseThrow();
        InventoryDocument d = documents.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        em.refresh(d, LockModeType.PESSIMISTIC_WRITE);
        return d;
    }

    private void checkDraft(InventoryDocument d, long version) {
        if (d.getStatus() != DocumentStatus.DRAFT)
            fail("Документ уже проведён. Изменение и повторное проведение запрещены.");
        if (d.getVersion() != version)
            fail("Документ уже изменён. Обновите страницу.");
    }

    private void postLocked(InventoryDocument d, Long actorId) {
        if (d.getStatus() != DocumentStatus.DRAFT)
            fail("Документ уже проведён.");
        if (d.getItems().isEmpty())
            fail("Нельзя провести пустой документ.");
        if (d.getDocumentDate().isAfter(LocalDate.now(clock)))
            fail("Дата документа не может быть в будущем.");
        if (!d.getWarehouse().isActive())
            fail("Склад отключён.");
        if (d.getType() == DocumentType.RECEIPT && !d.getSupplier().isActive())
            fail("Поставщик отключён.");
        if (d.getType() == DocumentType.ISSUE && !d.getRecipientDepartment().isActive())
            fail("Подразделение отключено.");
        Map<Long, BigDecimal> demand = new TreeMap<>();
        d.getItems().stream().map(i -> i.getBatch().getMaterial().getId()).distinct().sorted()
                .forEach(id -> materials.findLockedById(id).orElseThrow());
        for (InventoryDocumentItem item : d.getItems()) {
            decimal(item.getQuantity(), false, "Количество");
            MaterialBatch b = item.getBatch();
            Material material = materials.findLockedById(b.getMaterial().getId()).orElseThrow();
            if (d.getType() != DocumentType.WRITE_OFF && !material.isActive())
                fail("Материал «" + material.getName() + "» находится в архиве.");
            if (material.isHasExpiry() && b.getExpiresOn() == null)
                fail("Для партии не указан срок годности.");
            if (d.getType() == DocumentType.ISSUE && b.getExpiresOn() != null
                    && b.getExpiresOn().isBefore(LocalDate.now(clock)))
                fail("Партия «" + b.getBatchNumber() + "» просрочена. Допустимо только списание.");
            if (d.getType() == DocumentType.RECEIPT) {
                Long count = em.createQuery(
                        "select count(i) from InventoryDocumentItem i where i.batch.id=:batch and i.document.type=:type and i.document.status=:status and i.document.id<>:doc",
                        Long.class)
                        .setParameter("batch", b.getId()).setParameter("type", DocumentType.RECEIPT)
                        .setParameter("status", DocumentStatus.POSTED).setParameter("doc", d.getId()).getSingleResult();
                if (count > 0)
                    fail("Учетная партия уже оприходована.");
            }
            demand.merge(b.getId(), item.getQuantity(), BigDecimal::add);
        }
        if (d.getType() != DocumentType.RECEIPT) {
            for (var entry : demand.entrySet()) {
                Long receipts = em.createQuery(
                        "select count(i) from InventoryDocumentItem i where i.batch.id=:batch and i.document.type=:type and i.document.status=:status and i.document.warehouse.id=:warehouse and i.document.documentDate<=:date",
                        Long.class)
                        .setParameter("batch", entry.getKey()).setParameter("type", DocumentType.RECEIPT)
                        .setParameter("status", DocumentStatus.POSTED)
                        .setParameter("warehouse", d.getWarehouse().getId()).setParameter("date", d.getDocumentDate())
                        .getSingleResult();
                if (receipts == 0)
                    fail("Партия не была оприходована на этот склад к дате документа.");
                BigDecimal available = stock.available(d.getWarehouse().getId(), entry.getKey());
                if (available.compareTo(entry.getValue()) < 0)
                    fail("Недостаточно материала в партии №" + entry.getKey() + ". Доступно: "
                            + available.stripTrailingZeros().toPlainString() + ".");
            }
        }
        Instant now = clock.instant();
        d.setStatus(DocumentStatus.POSTED);
        d.setPostedAt(now);
        em.persist(new InventoryEvent(d, actor(actorId), now));
        documents.flush();
    }

    private User actor(Long id) {
        User user = users.findById(id).orElseThrow(() -> invalid("Пользователь не найден."));
        if (!user.isActive() || user.isPasswordChangeRequired()
                || (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.STOREKEEPER))
            fail("Нет права на складскую операцию.");
        return user;
    }

    private void decimal(BigDecimal value, boolean zeroAllowed, String field) {
        if (value == null || value.signum() < 0 || (!zeroAllowed && value.signum() == 0) || value.scale() > 4
                || value.precision() - value.scale() > 15)
            fail(field + ": положительное число до 4 знаков после запятой.");
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private void fail(String message) {
        throw invalid(message);
    }
}
