package uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

/** JPA entity for crm_settings. */
@Entity
@Table(name = "crm_settings")
public class CrmSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "ai_employee_id")
    private Integer aiEmployeeId;

    @Column(name = "task_type_id")
    private Integer taskTypeId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
    }

    public Integer getAiEmployeeId() {
        return aiEmployeeId;
    }

    public void setAiEmployeeId(Integer aiEmployeeId) {
        this.aiEmployeeId = aiEmployeeId;
    }

    public Integer getTaskTypeId() {
        return taskTypeId;
    }

    public void setTaskTypeId(Integer taskTypeId) {
        this.taskTypeId = taskTypeId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
