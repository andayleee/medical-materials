package ru.vkr.materials.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.security.LaboratoryPrincipal;
import static ru.vkr.materials.service.ReportTable.row;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ReportService {
    private final InventoryReadService inventory;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final WarehouseRepository warehouses;
    private final DepartmentRepository departments;
    private final Clock clock;

    public ReportService(InventoryReadService inventory, JdbcTemplate jdbc, UserRepository users,
            WarehouseRepository warehouses, DepartmentRepository departments, Clock clock) {
        this.inventory = inventory;
        this.jdbc = jdbc;
        this.users = users;
        this.warehouses = warehouses;
        this.departments = departments;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ReportTable expiry(int days, String mode, Long warehouseId, String q) {
        if (days < 1 || days > 365)
            bad("Период должен быть от 1 до 365 дней.");
        if (!Set.of("all", "expired", "soon").contains(mode))
            bad("Выберите допустимый режим сроков годности.");
        checkWarehouse(warehouseId);
        LocalDate today = LocalDate.now(clock), limit = today.plusDays(days);
        var rows = inventory.stocks(q).stream().filter(s -> warehouseId == null || warehouseId.equals(s.warehouseId()))
                .filter(s -> s.quantity().signum() > 0 && s.expiresOn() != null)
                .filter(s -> mode.equals("expired") ? s.expiresOn().isBefore(today)
                        : mode.equals("soon") ? !s.expiresOn().isBefore(today) && !s.expiresOn().isAfter(limit)
                                : !s.expiresOn().isAfter(limit))
                .map(s -> row(s.warehouse(), s.code(), s.material(), s.batch(), s.expiresOn(),
                        s.expired() ? "Просрочена" : "Истекает", ChronoUnit.DAYS.between(today, s.expiresOn()),
                        s.quantity(), s.unit()))
                .toList();
        return new ReportTable("Контроль сроков годности",
                "На " + today + " · ближайшие " + days + " дней · только партии с положительным остатком",
                List.of("Склад", "Код", "Материал", "Партия", "Годен до", "Статус", "Дней до срока", "Остаток",
                        "Ед. изм."),
                rows);
    }

    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ReportTable stocks(LocalDate date, Long warehouseId, String q) {
        if (date == null || date.isAfter(LocalDate.now(clock)))
            bad("Дата остатка должна быть не позднее сегодняшней.");
        checkWarehouse(warehouseId);
        var rows = inventory.stocksAt(q, date).stream()
                .filter(s -> warehouseId == null || warehouseId.equals(s.warehouseId()))
                .map(s -> row(s.warehouse(), s.code(), s.material(), s.batch(), s.expiresOn(), s.quantity(), s.unit(),
                        s.expired() ? "Просрочена" : ""))
                .toList();
        return new ReportTable("Остатки на складе", "На конец " + date + " · по датам проведённых документов",
                List.of("Склад", "Код", "Материал", "Партия", "Годен до", "Остаток", "Ед. изм.", "Примечание"), rows);
    }

    private record Movement(Long warehouseId, Long materialId, String warehouse, String code, String material,
            String unit, LocalDate date, DocumentType type, BigDecimal quantity) {
    }

    private record Key(Long warehouseId, Long materialId) {
    }

    private static class Totals {
        final Movement first;
        BigDecimal opening = BigDecimal.ZERO, received = BigDecimal.ZERO, issued = BigDecimal.ZERO,
                writtenOff = BigDecimal.ZERO;
        boolean activity;

        Totals(Movement first) {
            this.first = first;
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ReportTable movement(LocalDate from, LocalDate to, Long warehouseId, String q) {
        period(from, to);
        checkWarehouse(warehouseId);
        String query = q.strip().toLowerCase(Locale.ROOT);
        String filter = warehouseId == null ? "" : " AND d.warehouses_id=?";
        Object[] args = warehouseId == null ? new Object[] { to } : new Object[] { to, warehouseId };
        var movements = jdbc.query(
                """
                        SELECT w.id_warehouses,m.id_materials,w.name warehouse,m.code,m.name material,u.symbol,
                               d.document_date,d.type,i.quantity
                        FROM inventory_document_items i JOIN inventory_documents d ON d.id_inventory_documents=i.inventory_documents_id
                        JOIN warehouses w ON w.id_warehouses=d.warehouses_id JOIN material_batches b ON b.id_material_batches=i.material_batches_id
                        JOIN materials m ON m.id_materials=b.materials_id JOIN units u ON u.id_units=m.units_id
                        WHERE d.status='POSTED' AND d.document_date<=?
                        """
                        + filter + " ORDER BY w.name,m.code,m.id_materials",
                (rs, n) -> new Movement(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getObject(7, LocalDate.class), DocumentType.valueOf(rs.getString(8)),
                        rs.getBigDecimal(9)),
                args);
        Map<Key, Totals> totals = new LinkedHashMap<>();
        for (Movement m : movements) {
            if (!(m.code() + " " + m.material()).toLowerCase(Locale.ROOT).contains(query))
                continue;
            Totals t = totals.computeIfAbsent(new Key(m.warehouseId(), m.materialId()), key -> new Totals(m));
            if (m.date().isBefore(from))
                t.opening = t.opening.add(m.type() == DocumentType.RECEIPT ? m.quantity() : m.quantity().negate());
            else {
                t.activity = true;
                switch (m.type()) {
                    case RECEIPT -> t.received = t.received.add(m.quantity());
                    case ISSUE -> t.issued = t.issued.add(m.quantity());
                    case WRITE_OFF -> t.writtenOff = t.writtenOff.add(m.quantity());
                }
            }
        }
        var rows = totals.values().stream().filter(t -> t.activity || t.opening.signum() != 0)
                .map(t -> row(t.first.warehouse(), t.first.code(), t.first.material(), t.first.unit(), t.opening,
                        t.received, t.issued, t.writtenOff,
                        t.opening.add(t.received).subtract(t.issued).subtract(t.writtenOff)))
                .toList();
        return new ReportTable("Движение материалов", from + " — " + to + " · по датам проведённых документов",
                List.of("Склад", "Код", "Материал", "Ед. изм.", "Начальный остаток", "Поступило", "Выдано", "Списано",
                        "Конечный остаток"),
                rows);
    }

    public ReportTable issues(LaboratoryPrincipal principal, LocalDate from, LocalDate to, Long warehouseId,
            Long departmentId, String q) {
        period(from, to);
        if (principal.role() == UserRole.RECIPIENT) {
            var account = users.findById(principal.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            Long own = account.getDepartment() == null ? null : account.getDepartment().getId();
            if (own == null || (departmentId != null && !departmentId.equals(own)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            departmentId = own;
        }
        checkWarehouse(warehouseId);
        if (departmentId != null && !departments.existsById(departmentId))
            bad("Подразделение не найдено.");
        List<Object> args = new ArrayList<>(List.of(from, to));
        String filter = "";
        if (warehouseId != null) {
            filter += " AND d.warehouses_id=?";
            args.add(warehouseId);
        }
        if (departmentId != null) {
            filter += " AND d.departments_id=?";
            args.add(departmentId);
        }
        String query = q.strip().toLowerCase(Locale.ROOT);
        var rows = jdbc
                .query("""
                        SELECT p.name department,w.name warehouse,m.code,m.name material,u.symbol,SUM(i.quantity) quantity
                        FROM inventory_document_items i JOIN inventory_documents d ON d.id_inventory_documents=i.inventory_documents_id
                        JOIN departments p ON p.id_departments=d.departments_id JOIN warehouses w ON w.id_warehouses=d.warehouses_id
                        JOIN material_batches b ON b.id_material_batches=i.material_batches_id JOIN materials m ON m.id_materials=b.materials_id
                        JOIN units u ON u.id_units=m.units_id
                        WHERE d.status='POSTED' AND d.type='ISSUE' AND d.document_date BETWEEN ? AND ?
                        """
                        + filter
                        + " GROUP BY p.id_departments,p.name,w.id_warehouses,w.name,m.id_materials,m.code,m.name,u.symbol ORDER BY p.name,w.name,m.code,m.id_materials",
                        (rs, n) -> row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getString(5), rs.getBigDecimal(6)),
                        args.toArray())
                .stream().filter(r -> (r.get(2) + " " + r.get(3)).toLowerCase(Locale.ROOT).contains(query)).toList();
        return new ReportTable("Выдачи по подразделениям", from + " — " + to + " · только проведённые выдачи",
                List.of("Подразделение", "Склад", "Код", "Материал", "Ед. изм.", "Выдано"), rows);
    }

    private void period(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now(clock)))
            bad("Укажите корректный период: начало не позже конца, конец не позже сегодняшнего дня.");
    }

    private void checkWarehouse(Long id) {
        if (id != null && !warehouses.existsById(id))
            bad("Склад не найден.");
    }

    private void bad(String message) {
        throw new IllegalArgumentException(message);
    }
}
