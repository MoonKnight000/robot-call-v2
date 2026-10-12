package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A calling job: who is called, when, how often and how many times.
 *
 * <p>Everything about <em>how the call sounds</em> — the scenario, the voice, the persona,
 * the model, the ambient sound, the trunks — belongs to the {@code AiAgent} this campaign
 * names (V12). A campaign used to carry both, which meant running the same script with a
 * Russian voice required cloning the whole campaign, and an inbound call, having no
 * campaign at all, could not be given those settings by any means.
 *
 * @param aiAgentId   the agent whose voice and script this campaign's calls run under; required
 *                    for {@link DialMode#AI}, optional for the operator modes
 * @param priority    1 (low) … 5 (urgent): the dialer splits each tick's batch between the
 *                    active campaigns in proportion to it, so a debt campaign at 5 gets
 *                    five lines for every one a survey at 1 gets
 * @param retryPolicy per-disposition retry rules; {@link RetryPolicy#NONE} for the defaults
 * @param startAt     when a started campaign goes ACTIVE; in the future it waits as SCHEDULED, null means now
 * @param endAt       when an ACTIVE campaign is closed as COMPLETED whatever targets remain; null means never
 * @param budgetUzs   spend ceiling for the whole campaign; the dialer pauses it once settled calls reach it, null for none
 * @param dailyBudgetUzs the same per company-local day; the pause lifts on the operator's resume
 * @param version     the optimistic-lock counter the row had when read; an edit is written only
 *                    while the row still has it (0 for a campaign not saved yet)
 * @param dialMode    who talks to the clients; {@link DialMode#AI} when not given
 * @param callQueueId the operator queue whose members take the calls; required for every mode but AI
 * @param maxAiOverflowPercent PREDICTIVE: the share of answered calls the AI agent may take, over the
 *                    last half hour, before the dialer slows to one call per free operator
 * @param targetOrdering which due targets the dialer claims first; {@link TargetOrdering#FIFO} when not given
 */
public record Campaign(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        int dailyCallCap,
        Long aiAgentId,
        long companyId,
        Long createdBy,
        RecurrenceType recurrenceType,
        Integer recurringDayOfMonth,
        String cronExpression,
        boolean autoResetTargets,
        Instant lastRunAt,
        int priority,
        RetryPolicy retryPolicy,
        Instant startAt,
        Instant endAt,
        Long budgetUzs,
        Long dailyBudgetUzs,
        long version,
        DialMode dialMode,
        Long callQueueId,
        int maxAiOverflowPercent,
        TargetOrdering targetOrdering
) {

    public static final int DEFAULT_PRIORITY = 3;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 5;
    public static final int DEFAULT_MAX_AI_OVERFLOW_PERCENT = 20;

    public Campaign {
        priority = priority < MIN_PRIORITY || priority > MAX_PRIORITY ? DEFAULT_PRIORITY : priority;
        retryPolicy = retryPolicy == null ? RetryPolicy.NONE : retryPolicy;
        dialMode = dialMode == null ? DialMode.AI : dialMode;
        targetOrdering = targetOrdering == null ? TargetOrdering.FIFO : targetOrdering;
    }

    public static CampaignBuilder builder() {
        return new CampaignBuilder();
    }

    /** A builder that starts from this campaign, for changing a few of its fields. */
    public CampaignBuilder toBuilder() {
        return new CampaignBuilder(this);
    }

    /**
     * The pre-V23 shape, kept only for the integration tests that still build a campaign this
     * way; everything else uses {@link #builder()}.
     */
    public Campaign(long id, String name, CampaignType type, CampaignStatus status,
                    LocalTime dialWindowStart, LocalTime dialWindowEnd, Set<DayOfWeek> dialDays,
                    int maxAttempts, int retryIntervalMinutes, int maxConcurrentCalls, int dailyCallCap,
                    long aiAgentId, long companyId, Long createdBy, RecurrenceType recurrenceType,
                    Integer recurringDayOfMonth, String cronExpression, boolean autoResetTargets,
                    Instant lastRunAt) {
        this(id, name, type, status, dialWindowStart, dialWindowEnd, dialDays, maxAttempts, retryIntervalMinutes,
                maxConcurrentCalls, dailyCallCap, aiAgentId, companyId, createdBy, recurrenceType,
                recurringDayOfMonth, cronExpression, autoResetTargets, lastRunAt, DEFAULT_PRIORITY,
                RetryPolicy.NONE, null, null, null, null, 0L, DialMode.AI, null,
                DEFAULT_MAX_AI_OVERFLOW_PERCENT, TargetOrdering.FIFO);
    }

    public Set<DayOfWeek> allowedDays() {
        return dialDays == null || dialDays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : dialDays;
    }
}
