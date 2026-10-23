package ru.vkr.materials.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.vkr.materials.model.User;
import ru.vkr.materials.model.UserRole;

public record LaboratoryPrincipal(Long id, String login, String passwordHash, String fullName,
                                  UserRole role, boolean active, long securityVersion) implements UserDetails {
    public static LaboratoryPrincipal from(User user) {
        return new LaboratoryPrincipal(user.getId(), user.getLogin(), user.getPasswordHash(),
                user.getFullName(), user.getRole(), user.isActive(), user.getSecurityVersion());
    }

    @Override public String getUsername() { return login; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return active; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String toString() { return "LaboratoryPrincipal[id=" + id + ", login=" + login + "]"; }
}
