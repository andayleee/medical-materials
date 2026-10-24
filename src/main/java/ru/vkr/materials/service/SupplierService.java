package ru.vkr.materials.service;

import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vkr.materials.model.Supplier;
import ru.vkr.materials.repository.SupplierRepository;
import ru.vkr.materials.web.form.SupplierForm;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
public class SupplierService {
    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    public List<Supplier> list(String q) {
        String query = q.strip().toLowerCase(Locale.ROOT);
        return suppliers.findAll(Sort.by("name")).stream()
                .filter(s -> s.getName().toLowerCase(Locale.ROOT).contains(query)
                        || (s.getInn() != null && s.getInn().contains(query)))
                .toList();
    }

    public SupplierForm form(Long id) {
        Supplier s = require(id);
        SupplierForm f = new SupplierForm();
        f.setName(s.getName());
        f.setInn(s.getInn());
        f.setContactInfo(s.getContactInfo());
        f.setActive(s.isActive());
        f.setVersion(s.getVersion());
        return f;
    }

    @Transactional
    public void save(Long id, SupplierForm f) {
        Supplier s = id == null ? new Supplier() : require(id);
        if (id != null && !Objects.equals(s.getVersion(), f.getVersion()))
            throw new IllegalArgumentException("Запись уже изменена. Откройте форму заново.");
        s.setName(f.getName().strip());
        s.setInn(f.getInn());
        s.setContactInfo(f.getContactInfo());
        s.setActive(f.isActive());
        suppliers.saveAndFlush(s);
    }

    private Supplier require(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
