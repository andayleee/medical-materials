package ru.vkr.materials.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import java.time.*;
import java.util.*;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.service.*;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.web.form.*;

@Controller
public class InventoryController {
    private final InventoryService operations;
    private final InventoryReadService read;
    private final SupplierService suppliers;
    private final WarehouseRepository warehouses;
    private final DepartmentRepository departments;
    private final MaterialRepository materials;
    private final Clock clock;

    public InventoryController(InventoryService operations, InventoryReadService read, SupplierService suppliers,
            WarehouseRepository warehouses, DepartmentRepository departments, MaterialRepository materials,
            Clock clock) {
        this.operations = operations;
        this.read = read;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.departments = departments;
        this.materials = materials;
        this.clock = clock;
    }

    @ModelAttribute("canOperate")
    public boolean canOperate(Authentication a) {
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> Set.of("ROLE_ADMIN", "ROLE_STOREKEEPER").contains(g.getAuthority()));
    }

    @ModelAttribute("operationNames")
    public Map<DocumentType, String> names() {
        return Map.of(DocumentType.RECEIPT, "Поступление", DocumentType.ISSUE, "Выдача", DocumentType.WRITE_OFF,
                "Списание");
    }

    @GetMapping("/inventory/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String suppliers(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("suppliers", suppliers.list(q));
        return "inventory/suppliers";
    }

    @GetMapping({ "/inventory/suppliers/new", "/inventory/suppliers/{id}/edit" })
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String supplier(@PathVariable(required = false) Long id, Model model) {
        model.addAttribute("supplierForm", id == null ? new SupplierForm() : suppliers.form(id));
        model.addAttribute("recordId", id);
        return "inventory/supplier-form";
    }

    @PostMapping({ "/inventory/suppliers", "/inventory/suppliers/{id}" })
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String saveSupplier(@PathVariable(required = false) Long id,
            @Valid @ModelAttribute SupplierForm supplierForm,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                suppliers.save(id, supplierForm);
                flash.addFlashAttribute("success", "Поставщик сохранён.");
                return "redirect:/inventory/suppliers";
            } catch (IllegalArgumentException ex) {
                errors.reject("supplier.invalid", ex.getMessage());
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                errors.reject("supplier.changed", "Запись изменилась. Откройте форму заново.");
            }
        }
        model.addAttribute("recordId", id);
        return "inventory/supplier-form";
    }

    @GetMapping("/inventory/stock")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String stock(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("stocks", read.stocks(q));
        return "inventory/stock";
    }

    @GetMapping("/operations")
    public String documents(@AuthenticationPrincipal LaboratoryPrincipal principal,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("documents", read.documents(principal, q, status));
        return "inventory/documents";
    }

    @GetMapping("/operations/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal LaboratoryPrincipal principal, Model model) {
        model.addAttribute("document", read.document(id, principal));
        model.addAttribute("items", read.items(id, principal));
        return "inventory/document";
    }

    @GetMapping("/operations/journal")
    public String journal(@AuthenticationPrincipal LaboratoryPrincipal principal, Model model) {
        model.addAttribute("events", read.journal(principal));
        return "inventory/journal";
    }

    @GetMapping("/inventory/documents/new")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String create(@RequestParam(defaultValue = "RECEIPT") DocumentType type, Model model) {
        InventoryForm f = new InventoryForm();
        f.setType(type);
        f.setDocumentDate(LocalDate.now(clock));
        f.getLines().add(new InventoryLineForm());
        var active = warehouses.findAll().stream().filter(Warehouse::isActive).toList();
        if (active.size() == 1)
            f.setWarehouseId(active.getFirst().getId());
        model.addAttribute("inventoryForm", f);
        return prepare(model, f);
    }

    @PostMapping("/inventory/documents")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String save(@Valid @ModelAttribute InventoryForm inventoryForm, BindingResult errors,
            @RequestParam(defaultValue = "draft") String mode,
            @AuthenticationPrincipal LaboratoryPrincipal principal, Model model) {
        if (!errors.hasErrors()) {
            try {
                Long id = operations.create(inventoryForm, principal.id(), mode.equals("post"));
                return "redirect:/operations/" + id;
            } catch (IllegalArgumentException ex) {
                errors.reject("inventory.invalid", ex.getMessage());
            }
        }
        return prepare(model, inventoryForm);
    }

    @PostMapping("/inventory/documents/{id}/post")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String post(@PathVariable Long id, @RequestParam long version,
            @AuthenticationPrincipal LaboratoryPrincipal principal, RedirectAttributes flash) {
        try {
            operations.post(id, version, principal.id());
            flash.addFlashAttribute("success", "Документ проведён. Остатки обновлены.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/operations/" + id;
    }

    @PostMapping("/inventory/documents/{id}/delete")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String delete(@PathVariable Long id, @RequestParam long version, RedirectAttributes flash) {
        try {
            operations.deleteDraft(id, version);
            flash.addFlashAttribute("success", "Черновик удалён.");
            return "redirect:/operations";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/operations/" + id;
        }
    }

    private String prepare(Model model, InventoryForm f) {
        model.addAttribute("warehouses",
                warehouses.findAll(Sort.by("name")).stream().filter(Warehouse::isActive).toList());
        model.addAttribute("suppliers", suppliers.list("").stream().filter(Supplier::isActive).toList());
        model.addAttribute("departments",
                departments.findAll(Sort.by("name")).stream().filter(Department::isActive).toList());
        model.addAttribute("materials",
                materials.findAll(Sort.by("name")).stream().filter(Material::isActive).toList());
        model.addAttribute("stockOptions", read.stocks(""));
        model.addAttribute("formType", f.getType() == null ? DocumentType.RECEIPT : f.getType());
        return "inventory/document-form";
    }
}
