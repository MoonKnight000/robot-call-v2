package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * JPA entity for campaign (PROJECT.md §6). The voice/persona/scenario columns moved to
 * {@code ai_agent} in V12; what is left is the dialling job itself.
 */
@Entity
@Table(name = "campaign")
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    @Column(name = "script_config", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String scriptConfig;

    @Column(name = "dial_window_start", nullable = false)
    private LocalTime dialWindowStart;

    @Column(name = "dial_window_end", nullable = false)
    private LocalTime dialWindowEnd;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "retry_interval_minutes", nullable = false)
    private int retryIntervalMinutes;

    @Column(name = "max_concurrent_calls", nullable = false)
    private int maxConcurrentCalls;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_dial_day", joinColumns = @JoinColumn(name = "campaign_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false)
    private Set<DayOfWeek> dialDays = EnumSet.noneOf(DayOfWeek.class);

    @Column(name = "daily_call_cap", nullable = false)
    private int dailyCallCap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(name = "ai_agent_id", nullable = false)
    private long aiAgentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false)
    private RecurrenceType recurrenceType = RecurrenceType.ONCE;

    @Column(name = "recurring_day_of_month")
    private Integer recurringDayOfMonth;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "auto_reset_targets", nullable = false)
    private boolean autoResetTargets;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CampaignType getType() {
        return type;
    }

    public void setType(CampaignType type) {
        this.type = type;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public String getScriptConfig() {
        return scriptConfig;
    }

    public void setScriptConfig(String scriptConfig) {
        this.scriptConfig = scriptConfig;
    }

    public LocalTime getDialWindowStart() {
        return dialWindowStart;
    }

    public void setDialWindowStart(LocalTime dialWindowStart) {
        this.dialWindowStart = dialWindowStart;
    }

    public LocalTime getDialWindowEnd() {
        return dialWindowEnd;
    }

    public void setDialWindowEnd(LocalTime dialWindowEnd) {
        this.dialWindowEnd = dialWindowEnd;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryIntervalMinutes() {
        return retryIntervalMinutes;
    }

    public void setRetryIntervalMinutes(int retryIntervalMinutes) {
        this.retryIntervalMinutes = retryIntervalMinutes;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
    }

    public Long getCreatedById() {
        return createdBy != null ? createdBy.getId() : null;
    }

    public Set<DayOfWeek> getDialDays() {
        return dialDays;
    }

    public void setDialDays(Set<DayOfWeek> dialDays) {
        this.dialDays = (dialDays == null || dialDays.isEmpty())
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(dialDays);
    }

    public int getDailyCallCap() {
        return dailyCallCap;
    }

    public void setDailyCallCap(int dailyCallCap) {
        this.dailyCallCap = dailyCallCap;
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

    public long getAiAgentId() {
        return aiAgentId;
    }

    public void setAiAgentId(long aiAgentId) {
        this.aiAgentId = aiAgentId;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(RecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
    }

    public Integer getRecurringDayOfMonth() {
        return recurringDayOfMonth;
    }

    public void setRecurringDayOfMonth(Integer recurringDayOfMonth) {
        this.recurringDayOfMonth = recurringDayOfMonth;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public boolean isAutoResetTargets() {
        return autoResetTargets;
    }

    public void setAutoResetTargets(boolean autoResetTargets) {
        this.autoResetTargets = autoResetTargets;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
}
