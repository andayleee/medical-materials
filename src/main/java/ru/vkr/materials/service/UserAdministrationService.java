package ru.vkr.materials.service;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.security.PasswordPolicy;
import ru.vkr.materials.web.form.UserForm;
import java.util.Locale;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class UserAdministrationService {
    public record UserRow(Long id, String login, String fullName, String roleName, String departmentName,
            boolean active, boolean passwordChangeRequired, long version) {
    }

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final InventoryDocumentRepository documents;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;

    public UserAdministrationService(UserRepository users, DepartmentRepository departments,
            InventoryDocumentRepository documents, PasswordEncoder passwords, EntityManager entityManager) {
        this.users = users;
        this.departments = departments;
        this.documents = documents;
        this.passwords = passwords;
        this.entityManager = entityManager;
    }

    public Page<UserRow> search(String query, int page) {
        String pattern = "%"
                + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return users.findAll((root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("login")), pattern, '\\'),
                cb.like(cb.function("translate", String.class, cb.lower(root.get("fullName")),
                        cb.literal("АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"),
                        cb.literal("абвгдеёжзийклмнопрстуфхцчшщъыьэюя")), pattern, '\\')),
                PageRequest.of(Math.max(0, page), 20, Sort.by("id"))).map(this::row);
    }

    public UserRow get(Long id) {
        return row(require(id));
    }

    public UserForm form(Long id) {
        User u = require(id);
        UserForm f = new UserForm();
        f.setLogin(u.getLogin());
        f.setFullName(u.getFullName());
        f.setRole(u.getRole());
        f.setDepartmentId(u.getDepartment() == null ? null : u.getDepartment().getId());
        f.setActive(u.isActive());
        f.setVersion(u.getSecurityVersion());
        return f;
    }

    @Transactional
    public void save(Long id, UserForm form, Long actingUserId) {
        lockAdministration();
        User user = id == null ? new User() : require(id);
        if (id != null && user.getSecurityVersion() != form.getVersion()) {
            throw new IllegalArgumentException("Запись уже изменена. Откройте форму заново.");
        }
        String login = form.getLogin().strip().toLowerCase(Locale.ROOT);
        users.findByLoginIgnoreCase(login).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new IllegalArgumentException("Этот логин уже занят.");
            }
        });
        if (id != null) {
            if (id.equals(actingUserId) && (!form.isActive() || form.getRole() != UserRole.ADMIN)) {
                throw new IllegalArgumentException("Нельзя отключить себя или снять с себя роль администратора.");
            }
            protectLastAdmin(user, form.isActive() && form.getRole() == UserRole.ADMIN);
        }
        Department department = form.getDepartmentId() == null ? null
                : departments.findById(form.getDepartmentId())
                        .orElseThrow(() -> new IllegalArgumentException("Подразделение не найдено."));
        if (form.getRole() == UserRole.RECIPIENT && department == null) {
            throw new IllegalArgumentException("Для сотрудника подразделения выберите подразделение.");
        }
        if (department != null && !department.isActive()) {
            throw new IllegalArgumentException("Выберите активное подразделение.");
        }
        String temporary = form.getTemporaryPassword();
        boolean reset = temporary != null && !temporary.isEmpty();
        if (id == null || reset) {
            PasswordPolicy.validate(temporary);
            if (id != null && passwords.matches(temporary, user.getPasswordHash())) {
                throw new IllegalArgumentException("Задайте новый временный пароль, отличный от текущего.");
            }
            user.setPasswordHash(passwords.encode(temporary));
            user.setPasswordChangeRequired(true);
        }
        user.setLogin(login);
        user.setFullName(form.getFullName().strip());
        user.setRole(form.getRole());
        user.setDepartment(department);
        user.setActive(form.isActive());
        users.saveAndFlush(user);
    }

    @Transactional
    public void delete(Long id, long expectedVersion, Long actingUserId) {
        lockAdministration();
        User user = require(id);
        if (user.getSecurityVersion() != expectedVersion) {
            throw new IllegalArgumentException("Запись уже изменена. Откройте подтверждение удаления заново.");
        }
        if (id.equals(actingUserId)) {
            throw new IllegalArgumentException("Нельзя удалить собственную учётную запись.");
        }
        protectLastAdmin(user, false);
        if (documents.existsByAuthor_Id(id)) {
            throw new IllegalArgumentException("Пользователь связан с документами. Отключите его вместо удаления.");
        }
        users.delete(user);
        users.flush();
    }

    private User require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    private UserRow row(User u) {
        return new UserRow(u.getId(), u.getLogin(), u.getFullName(), RoleNames.all().get(u.getRole()),
                u.getDepartment() == null ? "—" : u.getDepartment().getName(), u.isActive(),
                u.isPasswordChangeRequired(), u.getSecurityVersion());
    }

    private void protectLastAdmin(User user, boolean remainsActiveAdmin) {
        if (user.getRole() == UserRole.ADMIN && user.isActive() && !remainsActiveAdmin
                && users.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("В системе должен оставаться хотя бы один активный администратор.");
        }
    }

    private void lockAdministration() {
        entityManager.createNativeQuery("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE").executeUpdate();
    }
}
