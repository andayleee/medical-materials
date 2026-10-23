package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Warehouse;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
}
