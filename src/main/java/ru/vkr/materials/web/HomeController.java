package ru.vkr.materials.web;

import org.springframework.stereotype.Controller;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import ru.vkr.materials.security.LaboratoryPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping({ "/", "/login" })
    public String home(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        LaboratoryPrincipal principal = (LaboratoryPrincipal) authentication.getPrincipal();
        model.addAttribute("fullName", principal.fullName());
        model.addAttribute("isAdmin", principal.role() == ru.vkr.materials.model.UserRole.ADMIN);
        model.addAttribute("roleName", switch (principal.role()) {
            case ADMIN -> "Администратор";
            case STOREKEEPER -> "Сотрудник склада";
            case RECIPIENT -> "Сотрудник подразделения";
        });
        return "dashboard";
    }
}
