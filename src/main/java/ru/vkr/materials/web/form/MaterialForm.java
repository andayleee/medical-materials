package ru.vkr.materials.web.form;

import jakarta.validation.constraints.*;

public class MaterialForm {
    @NotBlank(message = "Укажите код материала")
    @Size(max = 64, message = "Код: не более 64 символов")
    private String code;
    @NotBlank(message = "Укажите название материала")
    @Size(max = 255, message = "Название: не более 255 символов")
    private String name;
    @NotNull(message = "Выберите единицу измерения")
    private Long unitId;
    @NotNull(message = "Выберите категорию")
    private Long categoryId;
    private boolean hasExpiry;
    private boolean active = true;
    @NotNull(message = "Версия отсутствует")
    private Long version = 0L;

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long value) {
        unitId = value;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long value) {
        categoryId = value;
    }

    public boolean isHasExpiry() {
        return hasExpiry;
    }

    public void setHasExpiry(boolean value) {
        hasExpiry = value;
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
