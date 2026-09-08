package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

import java.time.Instant;

@Entity
@Table(name = "conversion_goal")
public class ConversionGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "goal_key", nullable = false, length = 64)
    private String goalKey;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "attribution_window_hours", nullable = false)
    private Integer attributionWindowHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_model", nullable = false, length = 24)
    private AttributionModel attributionModel;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public String getGoalKey() { return goalKey; }
    public void setGoalKey(String goalKey) { this.goalKey = goalKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAttributionWindowHours() { return attributionWindowHours; }
    public void setAttributionWindowHours(Integer attributionWindowHours) { this.attributionWindowHours = attributionWindowHours; }
    public AttributionModel getAttributionModel() { return attributionModel; }
    public void setAttributionModel(AttributionModel attributionModel) { this.attributionModel = attributionModel; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
