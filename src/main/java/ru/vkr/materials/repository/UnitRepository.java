package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Unit;

public interface UnitRepository extends JpaRepository<Unit, Long> {
}
