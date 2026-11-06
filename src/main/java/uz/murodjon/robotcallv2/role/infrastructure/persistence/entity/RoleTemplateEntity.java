package uz.murodjon.robotcallv2.role.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** JPA entity for role_template and its role_template_permission rows. */
@Entity
@Table(name = "role_template")
public class RoleTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "assignable_by_company", nullable = false)
    private boolean assignableByCompany;

    // Eager: a template is never useful without its permissions and there are only a handful.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "role_template_permission", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "permission", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAssignableByCompany() {
        return assignableByCompany;
    }

    public void setAssignableByCompany(boolean assignableByCompany) {
        this.assignableByCompany = assignableByCompany;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /** In place: a swapped collection-table instance is re-inserted over rows that already exist. */
    public void setPermissions(Set<Permission> permissions) {
        if (permissions == this.permissions) {
            return;
        }
        this.permissions.clear();
        if (permissions != null) {
            this.permissions.addAll(permissions);
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
