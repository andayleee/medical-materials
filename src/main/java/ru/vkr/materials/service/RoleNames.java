package ru.vkr.materials.service;

import java.util.LinkedHashMap;
import java.util.Map;
import ru.vkr.materials.model.UserRole;

public final class RoleNames {
    private RoleNames() {
    }

    public static Map<UserRole, String> all() {
        Map<UserRole, String> roles = new LinkedHashMap<>();
        roles.put(UserRole.ADMIN, "Администратор");
        roles.put(UserRole.STOREKEEPER, "Сотрудник склада");
        roles.put(UserRole.RECIPIENT, "Сотрудник подразделения");
        return roles;
    }
}
