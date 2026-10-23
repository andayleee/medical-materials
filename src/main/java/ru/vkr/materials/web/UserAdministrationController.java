package ru.vkr.materials.web;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vkr.materials.repository.DepartmentRepository;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.service.RoleNames;
import ru.vkr.materials.service.UserAdministrationService;
import ru.vkr.materials.web.form.UserForm;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdministrationController {
    private final UserAdministrationService users;
    private final DepartmentRepository departments;

    public UserAdministrationController(UserAdministrationService users, DepartmentRepository departments) {
        this.users = users;
        this.departments = departments;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page, Model model) {
        String query = q.strip();
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }
        model.addAttribute("q", query);
        model.addAttribute("result", users.search(query, page));
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("userForm", new UserForm());
        return prepare(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("userForm", users.form(id));
        return prepare(model, id);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserForm form, BindingResult errors,
            @AuthenticationPrincipal LaboratoryPrincipal principal, Model model, RedirectAttributes flash) {
        return save(null, form, errors, principal, model, flash);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("userForm") UserForm form,
            BindingResult errors, @AuthenticationPrincipal LaboratoryPrincipal principal,
            Model model, RedirectAttributes flash) {
        return save(id, form, errors, principal, model, flash);
    }

    @GetMapping("/{id}/delete")
    public String confirmDelete(@PathVariable Long id, Model model) {
        model.addAttribute("user", users.get(id));
        return "admin/users/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam long version,
            @AuthenticationPrincipal LaboratoryPrincipal principal, RedirectAttributes flash) {
        try {
            users.delete(id, version, principal.id());
            flash.addFlashAttribute("success", "Пользователь удалён.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("error", "Пользователь связан с данными системы. Отключите его вместо удаления.");
        }
        return "redirect:/admin/users";
    }

    private String save(Long id, UserForm form, BindingResult errors, LaboratoryPrincipal principal,
            Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                users.save(id, form, principal.id());
                flash.addFlashAttribute("success",
                        id == null ? "Пользователь создан. Передайте ему временный пароль." : "Изменения сохранены.");
                return "redirect:/admin/users";
            } catch (IllegalArgumentException ex) {
                errors.reject("user.invalid", ex.getMessage());
            } catch (DataIntegrityViolationException ex) {
                errors.reject("user.conflict", "Не удалось сохранить. Возможно, этот логин уже занят.");
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                errors.reject("user.changed", "Запись уже изменена. Откройте форму заново.");
            }
        }
        form.setTemporaryPassword(null);
        return prepare(model, id);
    }

    private String prepare(Model model, Long id) {
        model.addAttribute("userId", id);
        model.addAttribute("roles", RoleNames.all());
        model.addAttribute("departments",
                departments.findAll(Sort.by("name")).stream().filter(d -> d.isActive()).toList());
        return "admin/users/form";
    }
}
