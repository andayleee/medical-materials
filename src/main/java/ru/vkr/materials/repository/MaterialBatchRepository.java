package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.MaterialBatch;

public interface MaterialBatchRepository extends JpaRepository<MaterialBatch, Long> {
    boolean existsByMaterial_Id(Long materialId);
    boolean existsByMaterial_Unit_Id(Long unitId);
}
