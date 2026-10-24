package ru.vkr.materials.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.UserRepository;
import ru.vkr.materials.security.LaboratoryPrincipal;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class InventoryReadService {
    public record Stock(Long batchId, Long warehouseId, String warehouse, String code, String material,
            String unit, String batch, LocalDate expiresOn, BigDecimal price, BigDecimal quantity, boolean expired,
            String supplierBatch) {
        public String barcode() {
            return batch;
        }
    }

    public record DocumentRow(Long id, String number, DocumentType type, DocumentStatus status, LocalDate date,
            String warehouse, String supplier, String recipient, String author, String reason, Instant postedAt,
            long version) {
    }

    public record ItemRow(String code, String material, String unit, String batch, LocalDate expiresOn,
            BigDecimal price, BigDecimal quantity, Long batchId, String supplierBatch) {
    }

    public record EventRow(Long documentId, String number, DocumentType type, String actor, Instant time) {
    }

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final Clock clock;

    public InventoryReadService(JdbcTemplate jdbc, UserRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public List<Stock> stocks(String q) {
        return stocksAt(q, LocalDate.now(clock));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public List<Stock> stocksAt(String q, LocalDate asOf) {
        String query = q.strip().toLowerCase(Locale.ROOT);
        return jdbc
                .query("""
                        SELECT b.id_material_batches, w.id_warehouses, w.name warehouse, m.code, m.name material,
                               u.symbol unit, b.batch_number, b.expires_on, b.purchase_unit_price,b.supplier_batch_number,
                               SUM(CASE d.type WHEN 'RECEIPT' THEN i.quantity ELSE -i.quantity END) quantity
                        FROM inventory_document_items i JOIN inventory_documents d ON d.id_inventory_documents=i.inventory_documents_id
                        JOIN warehouses w ON w.id_warehouses=d.warehouses_id
                        JOIN material_batches b ON b.id_material_batches=i.material_batches_id
                        JOIN materials m ON m.id_materials=b.materials_id JOIN units u ON u.id_units=m.units_id
                        WHERE d.status='POSTED' AND d.document_date<=?
                        GROUP BY b.id_material_batches,w.id_warehouses,w.name,m.code,m.name,u.symbol
                        HAVING SUM(CASE d.type WHEN 'RECEIPT' THEN i.quantity ELSE -i.quantity END) <> 0
                        ORDER BY b.expires_on ASC NULLS LAST, b.id_material_batches
                        """,
                        (rs, n) -> {
                            LocalDate expiry = rs.getObject("expires_on", LocalDate.class);
                            return new Stock(rs.getLong("id_material_batches"), rs.getLong("id_warehouses"),
                                    rs.getString("warehouse"),
                                    rs.getString("code"), rs.getString("material"), rs.getString("unit"),
                                    rs.getString("batch_number"), expiry,
                                    rs.getBigDecimal("purchase_unit_price"), rs.getBigDecimal("quantity"),
                                    expiry != null && expiry.isBefore(asOf), rs.getString("supplier_batch_number"));
                        }, asOf)
                .stream().filter(s -> (s.code() + " " + s.material() + " " + s.batch() + " " + s.barcode() + " "
                        + Objects.toString(s.supplierBatch(), "")).toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public BigDecimal available(Long warehouse, Long batch) {
        return jdbc.queryForObject(
                """
                        SELECT COALESCE(SUM(CASE d.type WHEN 'RECEIPT' THEN i.quantity ELSE -i.quantity END),0)
                        FROM inventory_document_items i JOIN inventory_documents d ON d.id_inventory_documents=i.inventory_documents_id
                        WHERE d.status='POSTED' AND d.warehouses_id=? AND i.material_batches_id=?
                        """,
                BigDecimal.class, warehouse, batch);
    }

    public List<DocumentRow> documents(LaboratoryPrincipal principal, String q, String status) {
        Long department = recipientDepartment(principal);
        if (principal.role() == UserRole.RECIPIENT && department == null)
            return List.of();
        return jdbc.query(documentSelect() + " ORDER BY d.id_inventory_documents DESC", (rs, n) -> mapDocument(rs))
                .stream()
                .filter(d -> principal.role() != UserRole.RECIPIENT || canRead(d.id(), department))
                .filter(d -> (d.number() + " " + Barcode.document(d.id())).toLowerCase(Locale.ROOT)
                        .contains(q.strip().toLowerCase(Locale.ROOT)))
                .filter(d -> status.isBlank() || d.status().name().equals(status)).toList();
    }

    public DocumentRow document(Long id, LaboratoryPrincipal principal) {
        var rows = jdbc.query(documentSelect() + " WHERE d.id_inventory_documents=?", (rs, n) -> mapDocument(rs), id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (principal.role() == UserRole.RECIPIENT && !canRead(id, recipientDepartment(principal)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return rows.getFirst();
    }

    public List<ItemRow> items(Long id, LaboratoryPrincipal principal) {
        document(id, principal);
        return jdbc.query(
                """
                        SELECT m.code,m.name,u.symbol,b.batch_number,b.expires_on,b.purchase_unit_price,i.quantity,b.id_material_batches,b.supplier_batch_number
                        FROM inventory_document_items i JOIN material_batches b ON b.id_material_batches=i.material_batches_id
                        JOIN materials m ON m.id_materials=b.materials_id JOIN units u ON u.id_units=m.units_id
                        WHERE i.inventory_documents_id=? ORDER BY i.line_number,i.id_inventory_document_items
                        """,
                (rs, n) -> new ItemRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDate.class), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getLong(8),
                        rs.getString(9)),
                id);
    }

    public List<EventRow> journal(LaboratoryPrincipal principal) {
        Long department = recipientDepartment(principal);
        if (principal.role() == UserRole.RECIPIENT && department == null)
            return List.of();
        String where = principal.role() == UserRole.RECIPIENT ? " WHERE d.type='ISSUE' AND d.departments_id=?" : "";
        Object[] args = principal.role() == UserRole.RECIPIENT ? new Object[] { department } : new Object[] {};
        return jdbc.query("""
                SELECT d.id_inventory_documents,d.number,d.type,u.full_name,e.created_at
                FROM inventory_events e JOIN inventory_documents d ON d.id_inventory_documents=e.inventory_documents_id
                JOIN users u ON u.id_users=e.users_id
                """ + where + " ORDER BY e.id_inventory_events DESC",
                (rs, n) -> new EventRow(rs.getLong(1), rs.getString(2),
                        DocumentType.valueOf(rs.getString(3)), rs.getString(4), rs.getTimestamp(5).toInstant()),
                args);
    }

    private Long recipientDepartment(LaboratoryPrincipal p) {
        if (p.role() != UserRole.RECIPIENT)
            return null;
        var user = users.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        return user.getDepartment() == null ? null : user.getDepartment().getId();
    }

    private boolean canRead(Long id, Long department) {
        return department != null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM inventory_documents WHERE id_inventory_documents=?
                  AND type='ISSUE' AND status='POSTED' AND departments_id=?)
                """, Boolean.class, id, department));
    }

    private String documentSelect() {
        return """
                SELECT d.*,w.name warehouse,s.name supplier,p.name recipient,u.full_name author
                FROM inventory_documents d JOIN warehouses w ON w.id_warehouses=d.warehouses_id
                JOIN users u ON u.id_users=d.users_id LEFT JOIN suppliers s ON s.id_suppliers=d.suppliers_id
                LEFT JOIN departments p ON p.id_departments=d.departments_id
                """;
    }

    private DocumentRow mapDocument(java.sql.ResultSet rs) throws java.sql.SQLException {
        var posted = rs.getTimestamp("posted_at");
        return new DocumentRow(rs.getLong("id_inventory_documents"), rs.getString("number"),
                DocumentType.valueOf(rs.getString("type")),
                DocumentStatus.valueOf(rs.getString("status")), rs.getObject("document_date", LocalDate.class),
                rs.getString("warehouse"),
                rs.getString("supplier"), rs.getString("recipient"), rs.getString("author"),
                rs.getString("write_off_reason"), posted == null ? null : posted.toInstant(),
                rs.getLong("record_version"));
    }
}
