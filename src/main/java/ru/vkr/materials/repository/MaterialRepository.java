package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Material;

public interface MaterialRepository extends JpaRepository<Material, Long> {
}
