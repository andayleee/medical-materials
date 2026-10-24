package ru.vkr.materials.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import ru.vkr.materials.web.form.*;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class CatalogService {
    public record UnitRow(Long id, String name, String symbol, long version) {
    }

    public record CategoryRow(Long id, String name, long version) {
    }

    public record MaterialRow(Long id, String code, String name, String unit, String category,
            boolean hasExpiry, boolean active, long version) {
    }

    private final UnitRepository units;
    private final CategoryRepository categories;
    private final MaterialRepository materials;
    private final MaterialBatchRepository batches;

    public CatalogService(UnitRepository units, CategoryRepository categories,
            MaterialRepository materials, MaterialBatchRepository batches) {
        this.units = units;
        this.categories = categories;
        this.materials = materials;
        this.batches = batches;
    }

    public List<UnitRow> units(String query) {
        String q = fold(query);
        return units.findAll(Sort.by("name")).stream()
                .filter(u -> fold(u.getName()).contains(q) || fold(u.getSymbol()).contains(q))
                .map(u -> new UnitRow(u.getId(), u.getName(), u.getSymbol(), u.getVersion())).toList();
    }

    public List<CategoryRow> categories(String query) {
        String q = fold(query);
        return categories.findAll(Sort.by("name")).stream().filter(c -> fold(c.getName()).contains(q))
                .map(c -> new CategoryRow(c.getId(), c.getName(), c.getVersion())).toList();
    }

    public Page<MaterialRow> materials(String query, boolean showArchived, int page) {
        String pattern = "%" + fold(query).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return materials.findAll((root, cq, cb) -> cb.and(
                showArchived ? cb.conjunction() : cb.isTrue(root.get("active")),
                cb.or(cb.like(fold(cb, root.get("name")), pattern, '\\'),
                        cb.like(fold(cb, root.get("code")), pattern, '\\'))),
                PageRequest.of(Math.max(0, page), 20, Sort.by("id")))
                .map(m -> new MaterialRow(m.getId(), m.getCode(), m.getName(), m.getUnit().getSymbol(),
                        m.getCategory().getName(), m.isHasExpiry(), m.isActive(), m.getVersion()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UnitForm unitForm(Long id) {
        Unit u = unit(id);
        UnitForm f = new UnitForm();
        f.setName(u.getName());
        f.setSymbol(u.getSymbol());
        f.setVersion(u.getVersion());
        return f;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public CategoryForm categoryForm(Long id) {
        Category c = category(id);
        CategoryForm f = new CategoryForm();
        f.setName(c.getName());
        f.setVersion(c.getVersion());
        return f;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    public MaterialForm materialForm(Long id) {
        Material m = material(id);
        MaterialForm f = new MaterialForm();
        f.setName(m.getName());
        f.setCode(m.getCode());
        f.setUnitId(m.getUnit().getId());
        f.setCategoryId(m.getCategory().getId());
        f.setHasExpiry(m.isHasExpiry());
        f.setActive(m.isActive());
        f.setVersion(m.getVersion());
        return f;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void saveUnit(Long id, UnitForm f) {
        Unit u = id == null ? new Unit() : units.findLockedById(id).orElseThrow(() -> missing("Единица измерения"));
        if (id != null) {
            checkVersion(u.getVersion(), f.getVersion());
        }
        if (id != null && !u.getSymbol().equals(f.getSymbol().strip()) && batches.existsByMaterial_Unit_Id(id)) {
            throw new IllegalArgumentException(
                    "Единица измерения уже используется в партиях. Её сокращение менять нельзя.");
        }
        u.setName(f.getName().strip());
        u.setSymbol(f.getSymbol().strip());
        units.saveAndFlush(u);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void saveCategory(Long id, CategoryForm f) {
        Category c = id == null ? new Category() : category(id);
        if (id != null) {
            checkVersion(c.getVersion(), f.getVersion());
        }
        c.setName(f.getName().strip());
        categories.saveAndFlush(c);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'STOREKEEPER')")
    public void saveMaterial(Long id, MaterialForm f) {
        Material m = id == null ? new Material() : materials.findLockedById(id).orElseThrow(() -> missing("Материал"));
        if (id != null) {
            checkVersion(m.getVersion(), f.getVersion());
            if ((!m.getUnit().getId().equals(f.getUnitId()) || m.isHasExpiry() != f.isHasExpiry())
                    && batches.existsByMaterial_Id(id)) {
                throw new IllegalArgumentException(
                        "У материала уже есть партии. Единицу измерения и правило срока годности менять нельзя.");
            }
        }
        m.setUnit(units.findById(f.getUnitId())
                .orElseThrow(() -> new IllegalArgumentException("Единица измерения не найдена.")));
        m.setCategory(categories.findById(f.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена.")));
        m.setName(f.getName().strip());
        m.setCode(f.getCode().strip());
        m.setHasExpiry(f.isHasExpiry());
        m.setActive(f.isActive());
        materials.saveAndFlush(m);
    }

    private Unit unit(Long id) {
        return units.findById(id).orElseThrow(() -> missing("Единица измерения"));
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> missing("Категория"));
    }

    private Material material(Long id) {
        return materials.findById(id).orElseThrow(() -> missing("Материал"));
    }

    private ResponseStatusException missing(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена: " + name);
    }

    private void checkVersion(long actual, Long submitted) {
        if (!Objects.equals(actual, submitted)) {
            throw new IllegalArgumentException("Запись уже изменена. Откройте форму заново.");
        }
    }

    private String fold(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    private Expression<String> fold(CriteriaBuilder cb, Expression<String> text) {
        return cb.function("translate", String.class, cb.lower(text),
                cb.literal("АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"), cb.literal("абвгдеёжзийклмнопрстуфхцчшщъыьэюя"));
    }
}
