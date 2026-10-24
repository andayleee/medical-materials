package ru.vkr.materials.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "material_batches")
public class MaterialBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_material_batches")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materials_id", nullable = false)
    private Material material;

    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "batch_number", nullable = false, length = 100, insertable = false, updatable = false)
    private String batchNumber;

    @Column(name = "supplier_batch_number", length = 100)
    private String supplierBatchNumber;

    @Column(name = "expires_on", nullable = true)
    private LocalDate expiresOn;

    @Column(name = "purchase_unit_price", nullable = true, precision = 19, scale = 4)
    private BigDecimal purchaseUnitPrice;

    public MaterialBatch() {
    }

    public Long getId() {
        return id;
    }

    public String getBarcode() {
        return batchNumber;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public String getSupplierBatchNumber() {
        return supplierBatchNumber;
    }

    public void setSupplierBatchNumber(String value) {
        supplierBatchNumber = value;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }

    public BigDecimal getPurchaseUnitPrice() {
        return purchaseUnitPrice;
    }

    public void setPurchaseUnitPrice(BigDecimal purchaseUnitPrice) {
        this.purchaseUnitPrice = purchaseUnitPrice;
    }
}
