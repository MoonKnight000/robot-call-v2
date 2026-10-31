package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;

import java.time.Instant;

/**
 * JPA entity for campaign_target (PROJECT.md §6).
 */
@Entity
@Table(name = "campaign_target")
public class CampaignTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CampaignEntity campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ContactEntity contact;

    @Column(nullable = false)
    private String phone;

    @Column
    private String language;

    @Column(name = "context_data", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String contextData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "do_not_call", nullable = false)
    private boolean doNotCall;

    /** Written only by the scoring update, never by a save; {@code scored_at} is not mapped at all. */
    @Column(name = "score", insertable = false, updatable = false)
    private Integer score;

    @Column(name = "best_call_hour", insertable = false, updatable = false)
    private Integer bestCallHour;
    /** Written only by the claim and the sequence advance, never by a save. */
    @Column(name = "step_id", insertable = false, updatable = false)
    private String stepId;

    @Column(name = "step_attempts", insertable = false, updatable = false)
    private int stepAttempts;

    /** Written only by the outcome evaluation (recordOutcome), never by a save. */
    @Column(name = "outcome", insertable = false, updatable = false)
    private String outcome;

    @Column(name = "converted", insertable = false, updatable = false)
    private boolean converted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CampaignEntity getCampaign() {
        return campaign;
    }

    public void setCampaign(CampaignEntity campaign) {
        this.campaign = campaign;
    }

    public long getCampaignId() {
        return campaign != null ? campaign.getId() : 0L;
    }

    public ContactEntity getContact() {
        return contact;
    }

    public void setContact(ContactEntity contact) {
        this.contact = contact;
    }

    public long getClientId() {
        return contact != null ? contact.getId() : 0L;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }

    public TargetStatus getStatus() {
        return status;
    }

    public void setStatus(TargetStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDoNotCall() {
        return doNotCall;
    }

    public void setDoNotCall(boolean doNotCall) {
        this.doNotCall = doNotCall;
    }

    public Integer getScore() {
        return score;
    }

    public String getStepId() {
        return stepId;
    }

    public int getStepAttempts() {
        return stepAttempts;
    }

    public String getOutcome() {
        return outcome;
    }

    public boolean isConverted() {
        return converted;
    }

    public Integer getBestCallHour() {
        return bestCallHour;
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
}
