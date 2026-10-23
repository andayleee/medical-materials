package ru.vkr.materials.web.form;

import jakarta.validation.constraints.*;

public class CategoryForm {
    @NotBlank(message = "Укажите название категории")
    @Size(max = 100, message = "Название: не более 100 символов")
    private String name;
    @NotNull(message = "Версия отсутствует")
    private Long version = 0L;

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        version = value;
    }
}
