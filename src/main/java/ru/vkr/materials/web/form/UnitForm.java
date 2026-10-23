package ru.vkr.materials.web.form;

import jakarta.validation.constraints.*;

public class UnitForm {
    @NotBlank(message = "Укажите название единицы измерения")
    @Size(max = 100, message = "Название: не более 100 символов")
    private String name;
    @NotBlank(message = "Укажите сокращение")
    @Size(max = 32, message = "Сокращение: не более 32 символов")
    private String symbol;
    @NotNull(message = "Версия отсутствует")
    private Long version = 0L;

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String value) {
        symbol = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        version = value;
    }
}
