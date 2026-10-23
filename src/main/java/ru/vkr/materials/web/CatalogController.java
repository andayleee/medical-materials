package ru.vkr.materials.web;

import jakarta.validation.Valid;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vkr.materials.service.CatalogService;
import ru.vkr.materials.web.form.*;

@Controller
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @ModelAttribute("canEditCatalog")
    public boolean canEdit(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @ModelAttribute("canEditMaterials")
    public boolean canEditMaterials(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_STOREKEEPER"));
    }

    @GetMapping("/catalog/units")
    public String units(@RequestParam(defaultValue = "") String q, Model model) {
        String query = query(q);
        model.addAttribute("q", query);
        model.addAttribute("units", catalog.units(query));
        return "catalog/units";
    }

    @GetMapping("/catalog/categories")
    public String categories(@RequestParam(defaultValue = "") String q, Model model) {
        String query = query(q);
        model.addAttribute("q", query);
        model.addAttribute("categories", catalog.categories(query));
        return "catalog/categories";
    }

    @GetMapping("/catalog/materials")
    public String materials(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean showArchived, @RequestParam(defaultValue = "0") int page,
            Model model) {
        String query = query(q);
        model.addAttribute("q", query);
        model.addAttribute("showArchived", showArchived);
        model.addAttribute("result", catalog.materials(query, showArchived, page));
        return "catalog/materials";
    }

    @GetMapping("/admin/catalog/units/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newUnit(Model model) {
        model.addAttribute("unitForm", new UnitForm());
        return unitPage(null, model);
    }

    @GetMapping("/admin/catalog/units/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editUnit(@PathVariable Long id, Model model) {
        model.addAttribute("unitForm", catalog.unitForm(id));
        return unitPage(id, model);
    }

    @PostMapping({ "/admin/catalog/units", "/admin/catalog/units/{id}" })
    @PreAuthorize("hasRole('ADMIN')")
    public String saveUnit(@PathVariable(required = false) Long id, @Valid @ModelAttribute UnitForm unitForm,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (save(errors, () -> catalog.saveUnit(id, unitForm))) {
            flash.addFlashAttribute("success", "Единица измерения сохранена.");
            return "redirect:/catalog/units";
        }
        return unitPage(id, model);
    }

    @GetMapping("/admin/catalog/categories/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newCategory(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        return categoryPage(null, model);
    }

    @GetMapping("/admin/catalog/categories/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editCategory(@PathVariable Long id, Model model) {
        model.addAttribute("categoryForm", catalog.categoryForm(id));
        return categoryPage(id, model);
    }

    @PostMapping({ "/admin/catalog/categories", "/admin/catalog/categories/{id}" })
    @PreAuthorize("hasRole('ADMIN')")
    public String saveCategory(@PathVariable(required = false) Long id,
            @Valid @ModelAttribute CategoryForm categoryForm,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (save(errors, () -> catalog.saveCategory(id, categoryForm))) {
            flash.addFlashAttribute("success", "Категория сохранена.");
            return "redirect:/catalog/categories";
        }
        return categoryPage(id, model);
    }

    @GetMapping("/admin/catalog/materials/new")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    public String newMaterial(Model model) {
        model.addAttribute("materialForm", new MaterialForm());
        return materialPage(null, model);
    }

    @GetMapping("/admin/catalog/materials/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    public String editMaterial(@PathVariable Long id, Model model) {
        model.addAttribute("materialForm", catalog.materialForm(id));
        return materialPage(id, model);
    }

    @PostMapping({ "/admin/catalog/materials", "/admin/catalog/materials/{id}" })
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    public String saveMaterial(@PathVariable(required = false) Long id,
            @Valid @ModelAttribute MaterialForm materialForm,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (save(errors, () -> catalog.saveMaterial(id, materialForm))) {
            flash.addFlashAttribute("success", "Материал сохранён.");
            return "redirect:/catalog/materials";
        }
        return materialPage(id, model);
    }

    private String unitPage(Long id, Model model) {
        model.addAttribute("recordId", id);
        return "catalog/unit-form";
    }

    private String categoryPage(Long id, Model model) {
        model.addAttribute("recordId", id);
        return "catalog/category-form";
    }

    private String materialPage(Long id, Model model) {
        model.addAttribute("recordId", id);
        model.addAttribute("units", catalog.units(""));
        model.addAttribute("categories", catalog.categories(""));
        return "catalog/material-form";
    }

    private boolean save(BindingResult errors, Runnable operation) {
        if (errors.hasErrors()) {
            return false;
        }
        try {
            operation.run();
            return true;
        } catch (IllegalArgumentException ex) {
            errors.reject("catalog.invalid", ex.getMessage());
        } catch (ObjectOptimisticLockingFailureException ex) {
            errors.reject("catalog.stale", "Запись уже изменена. Откройте форму заново.");
        }
        return false;
    }

    private String query(String q) {
        return q.strip().substring(0, Math.min(q.strip().length(), 100));
    }
}
