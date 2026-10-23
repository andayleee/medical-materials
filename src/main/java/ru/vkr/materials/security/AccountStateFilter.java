package ru.vkr.materials.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.vkr.materials.repository.UserRepository;

// Created only inside SecurityFilterChain, not registered as a second servlet filter.
public class AccountStateFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public AccountStateFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LaboratoryPrincipal principal) {
            var user = users.findById(principal.id()).orElse(null);
            if (user == null || !user.isActive() || user.getSecurityVersion() != principal.securityVersion()) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/login?accountChanged");
                return;
            }
            String path = request.getServletPath();
            // MockMvc and some servlet mappings supply an empty servletPath.
            if (path.isEmpty()) { path = request.getRequestURI().substring(request.getContextPath().length()); }
            boolean allowed = path.equals("/change-password") || path.equals("/logout")
                    || path.startsWith("/css/") || path.equals("/favicon.ico");
            if (user.isPasswordChangeRequired() && !allowed) {
                response.sendRedirect(request.getContextPath() + "/change-password");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
