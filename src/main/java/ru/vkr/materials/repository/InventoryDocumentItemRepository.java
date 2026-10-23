package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.InventoryDocumentItem;

public interface InventoryDocumentItemRepository extends JpaRepository<InventoryDocumentItem, Long> {
}
