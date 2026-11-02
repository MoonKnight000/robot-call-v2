package uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** JPA entity for integration_category: a heading the integration catalog is grouped under. */
@Entity
@Table(name = "integration_category")
public class IntegrationCategoryEntity {

    @Id
    @Column(name = "category_key", length = 32)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
