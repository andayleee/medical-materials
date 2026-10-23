package ru.vkr.materials.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.service.PasswordChangeService;

@Controller
public class PasswordChangeController {
    private final PasswordChangeService passwords;

    public PasswordChangeController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    @GetMapping("/change-password")
    public String form() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String change(@AuthenticationPrincipal LaboratoryPrincipal principal,
            @RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String confirmation, Model model, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            passwords.change(principal.id(), principal.securityVersion(), currentPassword, newPassword, confirmation);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "change-password";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?passwordChanged";
    }
}
