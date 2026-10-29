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

    @Column(name = "meeting_booked_status_id")
    private Integer meetingBookedStatusId;

    @Column(name = "promise_to_pay_status_id")
    private Integer promiseToPayStatusId;

    @Column(name = "not_interested_status_id")
    private Integer notInterestedStatusId;

    @Column(name = "callback_status_id")
    private Integer callbackStatusId;

    @Column(name = "new_lead_status_id")
    private Integer newLeadStatusId;

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

    public Integer getMeetingBookedStatusId() {
        return meetingBookedStatusId;
    }

    public void setMeetingBookedStatusId(Integer meetingBookedStatusId) {
        this.meetingBookedStatusId = meetingBookedStatusId;
    }

    public Integer getPromiseToPayStatusId() {
        return promiseToPayStatusId;
    }

    public void setPromiseToPayStatusId(Integer promiseToPayStatusId) {
        this.promiseToPayStatusId = promiseToPayStatusId;
    }

    public Integer getNotInterestedStatusId() {
        return notInterestedStatusId;
    }

    public void setNotInterestedStatusId(Integer notInterestedStatusId) {
        this.notInterestedStatusId = notInterestedStatusId;
    }

    public Integer getCallbackStatusId() {
        return callbackStatusId;
    }

    public void setCallbackStatusId(Integer callbackStatusId) {
        this.callbackStatusId = callbackStatusId;
    }

    public Integer getNewLeadStatusId() {
        return newLeadStatusId;
    }

    public void setNewLeadStatusId(Integer newLeadStatusId) {
        this.newLeadStatusId = newLeadStatusId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
