package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Unit;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Unit e where e.id = :id")
    java.util.Optional<Unit> findLockedById(Long id);
}
