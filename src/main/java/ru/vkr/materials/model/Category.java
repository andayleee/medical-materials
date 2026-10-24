package ru.vkr.materials.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categories")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Version
    @Column(name = "record_version", nullable = false)
    private long version;

    public long getVersion() {
        return version;
    }

    public Category() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
