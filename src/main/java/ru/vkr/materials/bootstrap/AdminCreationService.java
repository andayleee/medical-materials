package ru.vkr.materials.bootstrap;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.materials.model.User;
import ru.vkr.materials.model.UserRole;
import ru.vkr.materials.repository.UserRepository;

@Service
public class AdminCreationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;

    public AdminCreationService(UserRepository users, PasswordEncoder passwords, EntityManager entityManager) {
        this.users = users; this.passwords = passwords; this.entityManager = entityManager;
    }

    @Transactional
    public void createFirstAdmin(String login, String fullName, char[] password) {
        String normalized = login.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{3,100}")) {
            throw new IllegalArgumentException("Логин: от 3 до 100 латинских букв, цифр, точек, дефисов или подчёркиваний");
        }
        if (fullName.isBlank() || fullName.strip().length() > 255) {
            throw new IllegalArgumentException("Укажите имя длиной от 1 до 255 символов");
        }
        CharBuffer secret = CharBuffer.wrap(password);
        if (password.length < 10 || StandardCharsets.UTF_8.encode(secret).remaining() > 72) {
            throw new IllegalArgumentException("Пароль: не менее 10 символов и не более 72 байт UTF-8");
        }
        // Serializes simultaneous bootstrap commands, including different admin logins.
        entityManager.createNativeQuery("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE").executeUpdate();
        if (users.existsByRole(UserRole.ADMIN)) {
            throw new IllegalStateException("Администратор уже существует. Повторное создание первым запуском запрещено");
        }
        if (users.findByLoginIgnoreCase(normalized).isPresent()) {
            throw new IllegalStateException("Этот логин уже занят");
        }
        User admin = new User();
        admin.setLogin(normalized); admin.setFullName(fullName.strip());
        admin.setPasswordHash(passwords.encode(CharBuffer.wrap(password)));
        admin.setRole(UserRole.ADMIN); admin.setActive(true);
        users.saveAndFlush(admin);
    }
}
