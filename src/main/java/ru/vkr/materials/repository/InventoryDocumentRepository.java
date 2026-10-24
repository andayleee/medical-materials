package ru.vkr.materials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.materials.model.InventoryDocument;

public interface InventoryDocumentRepository extends JpaRepository<InventoryDocument, Long> {
    boolean existsByAuthor_Id(Long authorId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from InventoryDocument e where e.id = :id")
    java.util.Optional<InventoryDocument> findLockedById(Long id);

    @org.springframework.data.jpa.repository.Query("select d.warehouse.id from InventoryDocument d where d.id = :id")
    java.util.Optional<Long> warehouseId(Long id);
}
