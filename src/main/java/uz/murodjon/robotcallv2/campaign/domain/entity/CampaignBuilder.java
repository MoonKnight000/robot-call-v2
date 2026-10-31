package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.*;

import java.time.Instant;

/**
 * Collects a {@link Campaign}'s fields; obtained from {@link Campaign#builder()} or, to change
 * a few fields of an existing campaign, {@link Campaign#toBuilder()}.
 */
public class CampaignBuilder {

    private long id;
    private String name;
    private CampaignStatus status;
    private Long aiAgentId;
    private long companyId;
    private Long createdBy;
    private int priority;
    private CampaignSchedule schedule;
    private CampaignRetry retry;
    private CampaignLimits limits;
    private CampaignRecurrence recurrence;
    private CampaignDialing dialing;
    private CampaignNumbers numbers;
    private Instant lastRunAt;
    private CampaignKind kind;
    private CampaignSequence sequence;
    private AutoEnrollment autoEnroll;
    private Instant consentConfirmedAt;
    private long version;

    CampaignBuilder() {
    }

    CampaignBuilder(Campaign source) {
        this.id = source.id();
        this.name = source.name();
        this.status = source.status();
        this.aiAgentId = source.aiAgentId();
        this.companyId = source.companyId();
        this.createdBy = source.createdBy();
        this.priority = source.priority();
        this.schedule = source.schedule();
        this.retry = source.retry();
        this.limits = source.limits();
        this.recurrence = source.recurrence();
        this.dialing = source.dialing();
        this.numbers = source.numbers();
        this.lastRunAt = source.lastRunAt();
        this.kind = source.kind();
        this.sequence = source.sequence();
        this.autoEnroll = source.autoEnroll();
        this.consentConfirmedAt = source.consentConfirmedAt();
        this.version = source.version();
    }

    public CampaignBuilder id(long id) {
        this.id = id;
        return this;
    }

    public CampaignBuilder name(String name) {
        this.name = name;
        return this;
    }

    public CampaignBuilder status(CampaignStatus status) {
        this.status = status;
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

    public CampaignBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public CampaignBuilder schedule(CampaignSchedule schedule) {
        this.schedule = schedule;
        return this;
    }

    public CampaignBuilder retry(CampaignRetry retry) {
        this.retry = retry;
        return this;
    }

    public CampaignBuilder limits(CampaignLimits limits) {
        this.limits = limits;
        return this;
    }

    public CampaignBuilder recurrence(CampaignRecurrence recurrence) {
        this.recurrence = recurrence;
        return this;
    }

    public CampaignBuilder dialing(CampaignDialing dialing) {
        this.dialing = dialing;
        return this;
    }

    public CampaignBuilder numbers(CampaignNumbers numbers) {
        this.numbers = numbers;
        return this;
    }

    public CampaignBuilder lastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
        return this;
    }

    public CampaignBuilder kind(CampaignKind kind) {
        this.kind = kind;
        return this;
    }

    public CampaignBuilder sequence(CampaignSequence sequence) {
        this.sequence = sequence;
        return this;
    }

    public CampaignBuilder autoEnroll(AutoEnrollment autoEnroll) {
        this.autoEnroll = autoEnroll;
        return this;
    }

    public CampaignBuilder consentConfirmedAt(Instant consentConfirmedAt) {
        this.consentConfirmedAt = consentConfirmedAt;
        return this;
    }

    public CampaignBuilder version(long version) {
        this.version = version;
        return this;
    }

    public Campaign build() {
        return new Campaign(id, name, status, aiAgentId, companyId, createdBy, priority,
                schedule, retry, limits, recurrence, dialing, numbers, lastRunAt, kind, sequence, autoEnroll, consentConfirmedAt, version);
    }
}
