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

    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    @Column(name = "expires_on", nullable = true)
    private LocalDate expiresOn;

    @Column(name = "purchase_unit_price", nullable = true, precision = 19, scale = 4)
    private BigDecimal purchaseUnitPrice;

    public MaterialBatch() {
    }

    public Long getId() {
        return id;
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

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
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
