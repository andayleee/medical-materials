package ru.vkr.materials.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "inventory_documents")
public class InventoryDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventory_documents")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouses_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "suppliers_id", nullable = true)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "departments_id", nullable = true)
    private Department recipientDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private User author;

    @Column(name = "number", nullable = false, length = 64)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "posted_at", nullable = true)
    private Instant postedAt;

    @Column(name = "write_off_reason", nullable = true, length = 1000)
    private String writeOffReason;

    @OneToMany(mappedBy = "document", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<InventoryDocumentItem> items = new ArrayList<>();

    public InventoryDocument() {
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public Department getRecipientDepartment() {
        return recipientDepartment;
    }

    public void setRecipientDepartment(Department recipientDepartment) {
        this.recipientDepartment = recipientDepartment;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public String getWriteOffReason() {
        return writeOffReason;
    }

    public void setWriteOffReason(String writeOffReason) {
        this.writeOffReason = writeOffReason;
    }

    public List<InventoryDocumentItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(InventoryDocumentItem item) {
        Objects.requireNonNull(item, "Строка документа обязательна");
        if (item.getDocument() != null && item.getDocument() != this) {
            throw new IllegalArgumentException("Строка уже принадлежит другому документу");
        }
        if (!items.contains(item)) {
            items.add(item);
        }
        item.setDocument(this);
    }
}
