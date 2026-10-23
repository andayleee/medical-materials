package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
