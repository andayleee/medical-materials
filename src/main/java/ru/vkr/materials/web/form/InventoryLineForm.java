package ru.vkr.materials.web.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.format.annotation.DateTimeFormat;
import ru.vkr.materials.model.DocumentType;

public class InventoryLineForm {
    private Long materialId;
    private Long batchId;
    @Size(max = 100, message = "Номер партии производителя: не более 100 символов")
    private String batchNumber;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiresOn;
    @DecimalMin(value = "0", message = "Цена не может быть отрицательной")
    @Digits(integer = 15, fraction = 4, message = "Цена: до 4 знаков после запятой")
    private BigDecimal purchaseUnitPrice;
    @NotNull(message = "Укажите количество")
    @DecimalMin(value = "0", inclusive = false, message = "Количество должно быть положительным")
    @Digits(integer = 15, fraction = 4, message = "Количество: до 4 знаков после запятой")
    private BigDecimal quantity;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long value) {
        materialId = value;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long value) {
        batchId = value;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String value) {
        batchNumber = value;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate value) {
        expiresOn = value;
    }

    public BigDecimal getPurchaseUnitPrice() {
        return purchaseUnitPrice;
    }

    public void setPurchaseUnitPrice(BigDecimal value) {
        purchaseUnitPrice = value;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal value) {
        quantity = value;
    }
}
