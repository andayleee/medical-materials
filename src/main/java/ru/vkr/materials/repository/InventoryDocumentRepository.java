package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.InventoryDocument;

public interface InventoryDocumentRepository extends JpaRepository<InventoryDocument, Long> {
}
