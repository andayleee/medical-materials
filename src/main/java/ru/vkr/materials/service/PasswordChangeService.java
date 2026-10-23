package ru.vkr.materials.service;

import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.materials.repository.UserRepository;
import ru.vkr.materials.security.PasswordPolicy;

@Service
public class PasswordChangeService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;

    public PasswordChangeService(UserRepository users, PasswordEncoder passwords, EntityManager entityManager) {
        this.users = users;
        this.passwords = passwords;
        this.entityManager = entityManager;
    }

    @Transactional
    public void change(Long id, long expectedVersion, String current, String replacement, String confirmation) {
        // Take the same table lock as administration before taking a row lock.
        // This prevents a password-change/reset lock-order deadlock.
        entityManager.createNativeQuery("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE").executeUpdate();
        var user = users.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Учётная запись не найдена."));
        if (!user.isActive() || user.getSecurityVersion() != expectedVersion) {
            throw new IllegalArgumentException("Учётная запись изменилась. Войдите заново.");
        }
        if (current == null || !passwords.matches(current, user.getPasswordHash())) {
            throw new IllegalArgumentException("Текущий пароль указан неверно.");
        }
        PasswordPolicy.validate(replacement);
        if (!replacement.equals(confirmation)) {
            throw new IllegalArgumentException("Новые пароли не совпадают.");
        }
        if (passwords.matches(replacement, user.getPasswordHash())) {
            throw new IllegalArgumentException("Новый пароль должен отличаться от временного или текущего.");
        }
        user.setPasswordHash(passwords.encode(replacement));
        user.setPasswordChangeRequired(false);
        users.saveAndFlush(user);
    }
}
