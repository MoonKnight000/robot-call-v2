package uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

/** JPA entity for integration_provider: one entry of the integration catalog. */
@Entity
@Table(name = "integration_provider")
public class IntegrationProviderEntity {

    @Id
    @Column(name = "provider_key", length = 32)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(name = "category_key", nullable = false, length = 32)
    private String category;

    /** Comma-separated {@code IntegrationAuthMethod} names, the preferred one first. */
    @Column(name = "auth_methods", nullable = false)
    private String authMethods;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    private StoredFileEntity iconFile;


    public StoredFileEntity getIconFile() {
        return iconFile;
    }

    public void setIconFile(StoredFileEntity iconFile) {
        this.iconFile = iconFile;
    }

    public Long getIconFileId() {
        return iconFile != null ? iconFile.getId() : null;
    }

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthMethods() {
        return authMethods;
    }

    public void setAuthMethods(String authMethods) {
        this.authMethods = authMethods;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }
}
