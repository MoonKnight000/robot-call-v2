package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

/**
 * Collects a {@link Campaign}'s fields; obtained from {@link Campaign#builder()} or, to change
 * a few fields of an existing campaign, {@link Campaign#toBuilder()}. Anything not set keeps
 * the value a campaign not saved yet has: id and version 0, no budget, no start or end moment;
 * the record itself fills in the priority, retry policy, dial mode and target ordering.
 */
public class CampaignBuilder {

    private long id;
    private String name;
    private CampaignType type;
    private CampaignStatus status;
    private LocalTime dialWindowStart;
    private LocalTime dialWindowEnd;
    private Set<DayOfWeek> dialDays;
    private int maxAttempts;
    private int retryIntervalMinutes;
    private int maxConcurrentCalls;
    private int dailyCallCap;
    private Long aiAgentId;
    private long companyId;
    private Long createdBy;
    private RecurrenceType recurrenceType;
    private Integer recurringDayOfMonth;
    private String cronExpression;
    private boolean autoResetTargets;
    private Instant lastRunAt;
    private int priority;
    private RetryPolicy retryPolicy;
    private Instant startAt;
    private Instant endAt;
    private Long budgetUzs;
    private Long dailyBudgetUzs;
    private long version;
    private DialMode dialMode;
    private Long callQueueId;
    private int maxAiOverflowPercent = Campaign.DEFAULT_MAX_AI_OVERFLOW_PERCENT;
    private TargetOrdering targetOrdering;

    CampaignBuilder() {
    }

    CampaignBuilder(Campaign source) {
        this.id = source.id();
        this.name = source.name();
        this.type = source.type();
        this.status = source.status();
        this.dialWindowStart = source.dialWindowStart();
        this.dialWindowEnd = source.dialWindowEnd();
        this.dialDays = source.dialDays();
        this.maxAttempts = source.maxAttempts();
        this.retryIntervalMinutes = source.retryIntervalMinutes();
        this.maxConcurrentCalls = source.maxConcurrentCalls();
        this.dailyCallCap = source.dailyCallCap();
        this.aiAgentId = source.aiAgentId();
        this.companyId = source.companyId();
        this.createdBy = source.createdBy();
        this.recurrenceType = source.recurrenceType();
        this.recurringDayOfMonth = source.recurringDayOfMonth();
        this.cronExpression = source.cronExpression();
        this.autoResetTargets = source.autoResetTargets();
        this.lastRunAt = source.lastRunAt();
        this.priority = source.priority();
        this.retryPolicy = source.retryPolicy();
        this.startAt = source.startAt();
        this.endAt = source.endAt();
        this.budgetUzs = source.budgetUzs();
        this.dailyBudgetUzs = source.dailyBudgetUzs();
        this.version = source.version();
        this.dialMode = source.dialMode();
        this.callQueueId = source.callQueueId();
        this.maxAiOverflowPercent = source.maxAiOverflowPercent();
        this.targetOrdering = source.targetOrdering();
    }

    public CampaignBuilder id(long id) {
        this.id = id;
        return this;
    }

    public CampaignBuilder name(String name) {
        this.name = name;
        return this;
    }

    public CampaignBuilder type(CampaignType type) {
        this.type = type;
        return this;
    }

    public CampaignBuilder status(CampaignStatus status) {
        this.status = status;
        return this;
    }

    public CampaignBuilder dialWindowStart(LocalTime dialWindowStart) {
        this.dialWindowStart = dialWindowStart;
        return this;
    }

    public CampaignBuilder dialWindowEnd(LocalTime dialWindowEnd) {
        this.dialWindowEnd = dialWindowEnd;
        return this;
    }

    public CampaignBuilder dialDays(Set<DayOfWeek> dialDays) {
        this.dialDays = dialDays;
        return this;
    }

    public CampaignBuilder maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    public CampaignBuilder retryIntervalMinutes(int retryIntervalMinutes) {
        this.retryIntervalMinutes = retryIntervalMinutes;
        return this;
    }

    public CampaignBuilder maxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
        return this;
    }

    public CampaignBuilder dailyCallCap(int dailyCallCap) {
        this.dailyCallCap = dailyCallCap;
        return this;
    }

    public CampaignBuilder aiAgentId(Long aiAgentId) {
        this.aiAgentId = aiAgentId;
        return this;
    }

    public CampaignBuilder companyId(long companyId) {
        this.companyId = companyId;
        return this;
    }

    public CampaignBuilder createdBy(Long createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public CampaignBuilder recurrenceType(RecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType;
        return this;
    }

    public CampaignBuilder recurringDayOfMonth(Integer recurringDayOfMonth) {
        this.recurringDayOfMonth = recurringDayOfMonth;
        return this;
    }

    public CampaignBuilder cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    public CampaignBuilder autoResetTargets(boolean autoResetTargets) {
        this.autoResetTargets = autoResetTargets;
        return this;
    }

    public CampaignBuilder lastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
        return this;
    }

    public CampaignBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public CampaignBuilder retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
    }

    public CampaignBuilder startAt(Instant startAt) {
        this.startAt = startAt;
        return this;
    }

    public CampaignBuilder endAt(Instant endAt) {
        this.endAt = endAt;
        return this;
    }

    public CampaignBuilder budgetUzs(Long budgetUzs) {
        this.budgetUzs = budgetUzs;
        return this;
    }

    public CampaignBuilder dailyBudgetUzs(Long dailyBudgetUzs) {
        this.dailyBudgetUzs = dailyBudgetUzs;
        return this;
    }

    public CampaignBuilder version(long version) {
        this.version = version;
        return this;
    }

    public CampaignBuilder dialMode(DialMode dialMode) {
        this.dialMode = dialMode;
        return this;
    }

    public CampaignBuilder callQueueId(Long callQueueId) {
        this.callQueueId = callQueueId;
        return this;
    }

    public CampaignBuilder maxAiOverflowPercent(int maxAiOverflowPercent) {
        this.maxAiOverflowPercent = maxAiOverflowPercent;
        return this;
    }

    public CampaignBuilder targetOrdering(TargetOrdering targetOrdering) {
        this.targetOrdering = targetOrdering;
        return this;
    }

    public Campaign build() {
        return new Campaign(id, name, type, status, dialWindowStart, dialWindowEnd, dialDays, maxAttempts,
                retryIntervalMinutes, maxConcurrentCalls, dailyCallCap, aiAgentId, companyId, createdBy,
                recurrenceType, recurringDayOfMonth, cronExpression, autoResetTargets, lastRunAt, priority,
                retryPolicy, startAt, endAt, budgetUzs, dailyBudgetUzs, version, dialMode, callQueueId,
                maxAiOverflowPercent, targetOrdering);
    }
}
