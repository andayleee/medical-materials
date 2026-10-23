package ru.vkr.materials.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() { }
    public static void validate(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Пароль должен содержать от 10 символов и не более 72 байт UTF-8.");
        }
    }
}
