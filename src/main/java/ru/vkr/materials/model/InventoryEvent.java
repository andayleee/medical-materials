package ru.vkr.materials.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory_events")
public class InventoryEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventory_events")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_documents_id", nullable = false)
    private InventoryDocument document;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private User actor;
    @Column(nullable = false, length = 32)
    private String action;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryEvent() {
    }

    public InventoryEvent(InventoryDocument document, User actor, Instant createdAt) {
        this.document = document;
        this.actor = actor;
        this.createdAt = createdAt;
        this.action = "POSTED";
    }
}
