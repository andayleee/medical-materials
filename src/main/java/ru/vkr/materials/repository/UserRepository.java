package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
