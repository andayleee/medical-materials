package ru.vkr.materials.web;

import java.time.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import ru.vkr.materials.export.CsvExport;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.model.*;
import ru.vkr.materials.service.*;
import static ru.vkr.materials.service.ReportTable.row;

@Controller
public class ReportController {
    private final ReportService reports;
    private final InventoryReadService inventory;
    private final WarehouseRepository warehouses;
    private final DepartmentRepository departments;
    private final Clock clock;

    public ReportController(ReportService reports, InventoryReadService inventory, WarehouseRepository warehouses,
            DepartmentRepository departments, Clock clock) {
        this.reports = reports;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.departments = departments;
        this.clock = clock;
    }

    @ModelAttribute("canOperate")
    public boolean canOperate(Authentication a) {
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> Set.of("ROLE_ADMIN", "ROLE_STOREKEEPER").contains(g.getAuthority()));
    }

    @GetMapping("/reports")
    public String index() {
        return "reports/index";
    }

    @GetMapping({ "/reports/expiry", "/reports/expiry.csv" })
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public Object expiry(@RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "all") String mode,
            @RequestParam(required = false) Long warehouseId, @RequestParam(defaultValue = "") String q, Model model,
            HttpServletRequest request) {
        ReportTable table = reports.expiry(days, mode, warehouseId, query(q));
        if (csv(request))
            return download(table, "expiry-" + LocalDate.now(clock) + "-" + days + "d-" + mode);
        common(model, "expiry", table, q, warehouseId);
        model.addAttribute("days", days);
        model.addAttribute("mode", mode);
        return "reports/table";
    }

    @GetMapping({ "/reports/stocks", "/reports/stocks.csv" })
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public Object stocks(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long warehouseId, @RequestParam(defaultValue = "") String q, Model model,
            HttpServletRequest request) {
        LocalDate asOf = date == null ? LocalDate.now(clock) : date;
        ReportTable table = reports.stocks(asOf, warehouseId, query(q));
        if (csv(request))
            return download(table, "stocks-" + asOf);
        common(model, "stocks", table, q, warehouseId);
        model.addAttribute("date", asOf);
        return "reports/table";
    }

    @GetMapping({ "/reports/movement", "/reports/movement.csv" })
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public Object movement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long warehouseId, @RequestParam(defaultValue = "") String q, Model model,
            HttpServletRequest request) {
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1) : from,
                end = to == null ? LocalDate.now(clock) : to;
        ReportTable table = reports.movement(start, end, warehouseId, query(q));
        if (csv(request))
            return download(table, "movement-" + start + "-" + end);
        common(model, "movement", table, q, warehouseId);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        return "reports/table";
    }

    @GetMapping({ "/reports/issues", "/reports/issues.csv" })
    public Object issues(@AuthenticationPrincipal LaboratoryPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "") String q, Model model, HttpServletRequest request) {
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1) : from,
                end = to == null ? LocalDate.now(clock) : to;
        ReportTable table = reports.issues(principal, start, end, warehouseId, departmentId, query(q));
        if (csv(request))
            return download(table, "issues-" + start + "-" + end);
        common(model, "issues", table, q, warehouseId);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("filterDepartment",
                departmentId == null ? null : departments.findById(departmentId).map(d -> d.getName()).orElse(null));
        model.addAttribute("departments",
                principal.role() == UserRole.RECIPIENT ? List.of() : departments.findAll(Sort.by("name")));
        return "reports/table";
    }

    @GetMapping("/operations/{id}/export.csv")
    public ResponseEntity<byte[]> document(@PathVariable Long id,
            @AuthenticationPrincipal LaboratoryPrincipal principal) {
        var doc = inventory.document(id, principal);
        var items = inventory.items(id, principal);
        String type = switch (doc.type()) {
            case RECEIPT -> "Поступление";
            case ISSUE -> "Выдача";
            case WRITE_OFF -> "Списание";
        };
        var table = new ReportTable(type + " " + doc.number(), "",
                List.of("Документ", "Тип", "Статус", "Дата", "Склад", "Поставщик / получатель", "Автор",
                        "Причина списания", "Код", "Материал", "Партия", "Годен до", "Количество", "Ед. изм.",
                        "Цена единицы"),
                items.stream().map(i -> row(doc.number(), type,
                        doc.status() == DocumentStatus.POSTED ? "Проведён" : "Черновик", doc.date(), doc.warehouse(),
                        doc.supplier() != null ? doc.supplier() : doc.recipient(), doc.author(), doc.reason(), i.code(),
                        i.material(), i.batch(), i.expiresOn(), i.quantity(), i.unit(), i.price())).toList());
        return download(table, "document-" + id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView invalid(IllegalArgumentException ex) {
        ModelAndView view = new ModelAndView("reports/error");
        view.addObject("error", ex.getMessage());
        view.setStatus(HttpStatus.BAD_REQUEST);
        return view;
    }

    private void common(Model model, String kind, ReportTable table, String q, Long warehouseId) {
        model.addAttribute("report", table);
        model.addAttribute("kind", kind);
        model.addAttribute("q", query(q));
        model.addAttribute("warehouseId", warehouseId);
        boolean canOperate = Boolean.TRUE.equals(model.getAttribute("canOperate"));
        model.addAttribute("warehouses", canOperate ? warehouses.findAll(Sort.by("name")) : List.of());
        model.addAttribute("filterWarehouse",
                warehouseId == null ? null : warehouses.findById(warehouseId).map(w -> w.getName()).orElse(null));
    }

    private boolean csv(HttpServletRequest request) {
        return request.getRequestURI().endsWith(".csv");
    }

    private ResponseEntity<byte[]> download(ReportTable table, String name) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(CsvExport.encode(table));
    }

    private String query(String q) {
        String trimmed = q.strip();
        return trimmed.substring(0, Math.min(trimmed.length(), 100));
    }
}
