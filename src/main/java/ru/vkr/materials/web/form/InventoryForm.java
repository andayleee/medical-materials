package ru.vkr.materials.web.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.format.annotation.DateTimeFormat;
import ru.vkr.materials.model.DocumentType;

public class InventoryForm {
    @NotNull(message = "Выберите тип операции")
    private DocumentType type;
    @Size(max = 64, message = "Номер: не более 64 символов")
    private String number;
    @NotNull(message = "Укажите дату")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate documentDate;
    @NotNull(message = "Выберите склад")
    private Long warehouseId;
    private Long supplierId;
    private Long departmentId;
    @Size(max = 1000, message = "Причина: не более 1000 символов")
    private String writeOffReason;
    @Valid
    @NotEmpty(message = "Добавьте хотя бы одну строку")
    @Size(max = 100, message = "Не более 100 строк")
    private java.util.List<InventoryLineForm> lines = new java.util.ArrayList<>();

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType value) {
        type = value;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String value) {
        number = value;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate value) {
        documentDate = value;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long value) {
        warehouseId = value;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long value) {
        supplierId = value;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long value) {
        departmentId = value;
    }

    public String getWriteOffReason() {
        return writeOffReason;
    }

    public void setWriteOffReason(String value) {
        writeOffReason = value;
    }

    public java.util.List<InventoryLineForm> getLines() {
        return lines;
    }

    public void setLines(java.util.List<InventoryLineForm> value) {
        lines = value;
    }
}
