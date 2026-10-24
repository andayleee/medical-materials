package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Warehouse;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Warehouse e where e.id = :id")
    java.util.Optional<Warehouse> findLockedById(Long id);
}
