package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
