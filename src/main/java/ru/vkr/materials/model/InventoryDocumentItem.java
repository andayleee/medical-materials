package ru.vkr.materials.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory_document_items")
public class InventoryDocumentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventory_document_items")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_documents_id", nullable = false)
    private InventoryDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_batches_id", nullable = false)
    private MaterialBatch batch;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    public InventoryDocumentItem() {
    }

    public Long getId() {
        return id;
    }

    public InventoryDocument getDocument() {
        return document;
    }

    public void setDocument(InventoryDocument document) {
        this.document = document;
    }

    public MaterialBatch getBatch() {
        return batch;
    }

    public void setBatch(MaterialBatch batch) {
        this.batch = batch;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
