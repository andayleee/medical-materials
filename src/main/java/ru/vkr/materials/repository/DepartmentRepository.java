package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
