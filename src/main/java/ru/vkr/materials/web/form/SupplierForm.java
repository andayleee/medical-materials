package ru.vkr.materials.web.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.format.annotation.DateTimeFormat;
import ru.vkr.materials.model.DocumentType;

public class SupplierForm {
    @NotBlank(message = "Укажите поставщика")
    @Size(max = 255)
    private String name;
    @Pattern(regexp = "(?:|[0-9]{10}|[0-9]{12})", message = "ИНН должен содержать 10 или 12 цифр")
    private String inn;
    @Size(max = 1000, message = "Контакты: не более 1000 символов")
    private String contactInfo;
    private boolean active = true;
    @NotNull
    private Long version = 0L;

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getInn() {
        return inn;
    }

    public void setInn(String value) {
        inn = value;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String value) {
        contactInfo = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        active = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        version = value;
    }
}
