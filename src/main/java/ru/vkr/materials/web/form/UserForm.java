package ru.vkr.materials.web.form;

import jakarta.validation.constraints.*;
import ru.vkr.materials.model.UserRole;

public class UserForm {
    @NotBlank(message = "Укажите логин")
    @Pattern(regexp = "[a-zA-Z0-9._-]{3,100}", message = "Логин: 3–100 латинских букв, цифр, точек, дефисов или подчёркиваний")
    private String login;
    @NotBlank(message = "Укажите имя")
    @Size(max = 255, message = "Имя: не более 255 символов")
    private String fullName;
    @NotNull(message = "Выберите роль")
    private UserRole role;
    private Long departmentId;
    private boolean active = true;
    private String temporaryPassword;
    @NotNull(message = "Версия записи отсутствует")
    private Long version = 0L;

    public String getLogin() {
        return login;
    }

    public void setLogin(String value) {
        login = value;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String value) {
        fullName = value;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole value) {
        role = value;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long value) {
        departmentId = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        active = value;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String value) {
        temporaryPassword = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        version = value;
    }
}
