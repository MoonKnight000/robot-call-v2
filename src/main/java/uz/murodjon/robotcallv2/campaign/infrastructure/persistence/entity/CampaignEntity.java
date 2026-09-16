package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
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
// An edit loads the row, changes the editable fields and saves it. Written in full, that
// save put back the status and last_run_at it had loaded — undoing a dialer's
// completeIfFinished or a platform pause that landed in between. Only changed columns now.
@DynamicUpdate
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

    // Eager (the mapper reads it outside any transaction); SUBSELECT: the dialer's
    // every-tick findActive loads all active campaigns' days in one query, not one each.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "campaign_dial_day", joinColumns = @JoinColumn(name = "campaign_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false)
    private Set<DayOfWeek> dialDays = EnumSet.noneOf(DayOfWeek.class);

    @Column(name = "daily_call_cap", nullable = false)
    private int dailyCallCap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id", nullable = false)
    private AiAgentEntity aiAgent;

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

    @Column(name = "priority", nullable = false)
    private int priority = 3;

    /** Per-disposition retry rules as JSON; null when the campaign uses the defaults. */
    @Column(name = "retry_policy")
    @JdbcTypeCode(SqlTypes.JSON)
    private String retryPolicy;

    /** When a SCHEDULED campaign goes ACTIVE; null starts on the operator's command. */
    @Column(name = "start_at")
    private Instant startAt;

    /** When an ACTIVE campaign is closed as COMPLETED regardless of targets left; null never. */
    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "budget_uzs")
    private Long budgetUzs;

    @Column(name = "daily_budget_uzs")
    private Long dailyBudgetUzs;

    /**
     * Bumped by an edit only. The dialer's status / last_run_at statements in
     * CampaignJpaRepository neither check nor bump it: an edit never writes those columns
     * (see @DynamicUpdate above), and a dialer tick must never fail because someone had the
     * form open.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

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

    /** In place: a swapped collection-table instance is re-inserted over rows that already exist. */
    public void setDialDays(Set<DayOfWeek> dialDays) {
        if (dialDays == this.dialDays) {
            return;
        }
        this.dialDays.clear();
        if (dialDays != null) {
            this.dialDays.addAll(dialDays);
        }
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

    public AiAgentEntity getAiAgent() {
        return aiAgent;
    }

    public void setAiAgent(AiAgentEntity aiAgent) {
        this.aiAgent = aiAgent;
    }

    public long getAiAgentId() {
        return aiAgent != null ? aiAgent.getId() : 0L;
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(String retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public Long getBudgetUzs() {
        return budgetUzs;
    }

    public void setBudgetUzs(Long budgetUzs) {
        this.budgetUzs = budgetUzs;
    }

    public Long getDailyBudgetUzs() {
        return dailyBudgetUzs;
    }

    public void setDailyBudgetUzs(Long dailyBudgetUzs) {
        this.dailyBudgetUzs = dailyBudgetUzs;
    }

    public long getVersion() {
        return version;
    }
}
