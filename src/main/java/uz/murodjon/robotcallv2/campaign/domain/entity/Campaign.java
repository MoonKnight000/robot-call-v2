package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
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
 * <p>The rest arrives in groups — {@link CampaignSchedule}, {@link CampaignRetry}, {@link
 * CampaignLimits}, {@link CampaignRecurrence}, {@link CampaignDialing} — each owning its own
 * defaults. A group is never null.
 *
 * @param aiAgentId the agent whose voice and script this campaign's calls run under; required
 *                  for {@link DialMode#AI}, optional for the operator modes
 * @param priority  1 (low) … 5 (urgent): the dialer splits each tick's batch between the
 *                  active campaigns in proportion to it
 * @param kind      how contacts get into the campaign; fixed at creation
 * @param sequence  the steps a contact goes through; null for a campaign created before
 *                  sequences, which behaves as one conversation step run by {@code aiAgentId}
 * @param autoEnroll the rules an AUTO_ENROLL campaign adds contacts by; never null, empty for a
 *                  STANDARD campaign
 * @param autoRetries double dial and automatic callbacks of a sequence campaign; never null
 * @param consentConfirmedAt when the legal consent for the contacts it calls was confirmed; null
 *                  until then, and a DRAFT campaign cannot be launched without it
 * @param numbers   the caller ID pool its calls present numbers from, and whether the one closest
 *                  to the callee's own is picked
 * @param version   the optimistic-lock counter the row had when read; an edit is written only
 *                  while the row still has it (0 for a campaign not saved yet)
 */
public record Campaign(
        long id,
        String name,
        CampaignStatus status,
        Long aiAgentId,
        long companyId,
        Long createdBy,
        int priority,
        CampaignSchedule schedule,
        CampaignRetry retry,
        CampaignLimits limits,
        CampaignRecurrence recurrence,
        CampaignDialing dialing,
        CampaignNumbers numbers,
        Instant lastRunAt,
        CampaignKind kind,
        CampaignSequence sequence,
        AutoEnrollment autoEnroll,
        CampaignAutoRetries autoRetries,
        Instant consentConfirmedAt,
        long version
) {

    public static final int DEFAULT_PRIORITY = 3;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 5;
    public static final int DEFAULT_MAX_AI_OVERFLOW_PERCENT = 20;

    public Campaign {
        kind = kind == null ? CampaignKind.STANDARD : kind;
        priority = priority < MIN_PRIORITY || priority > MAX_PRIORITY ? DEFAULT_PRIORITY : priority;
        if (schedule == null) {
            schedule = new CampaignSchedule(null, null, null, null, null);
        }
        if (retry == null) {
            retry = new CampaignRetry(0, 0, null);
        }
        if (limits == null) {
            limits = new CampaignLimits(0, 0, null, null, null);
        }
        if (recurrence == null) {
            recurrence = new CampaignRecurrence(null, null, null, false);
        }
        if (dialing == null) {
            dialing = new CampaignDialing(null, null, DEFAULT_MAX_AI_OVERFLOW_PERCENT, null);
        }
        if (numbers == null) {
            numbers = new CampaignNumbers(null, false, null);
        }
        if (autoRetries == null) {
            autoRetries = CampaignAutoRetries.NONE;
        }
        if (autoEnroll == null) {
            autoEnroll = AutoEnrollment.NONE;
        }
    }

    public static CampaignBuilder builder() {
        return new CampaignBuilder();
    }

    /** A builder that starts from this campaign, for changing a few of its fields. */
    public CampaignBuilder toBuilder() {
        return new CampaignBuilder(this);
    }

    /**
     * The legacy test shape, kept only for tests that still build a campaign this
     * way; everything else uses {@link #builder()}.
     */
    public Campaign(long id, String name, CampaignStatus status,
                    LocalTime dialWindowStart, LocalTime dialWindowEnd, Set<DayOfWeek> dialDays,
                    int maxAttempts, int retryIntervalMinutes, int maxConcurrentCalls, int dailyCallCap,
                    long aiAgentId, long companyId, Long createdBy, RecurrenceType recurrenceType,
                    Integer recurringDayOfMonth, String cronExpression, boolean autoResetTargets,
                    Instant lastRunAt) {
        this(id, name, status, aiAgentId, companyId, createdBy, DEFAULT_PRIORITY,
                new CampaignSchedule(null, null, dialWindowStart, dialWindowEnd, dialDays),
                new CampaignRetry(maxAttempts, retryIntervalMinutes, RetryPolicy.NONE),
                new CampaignLimits(maxConcurrentCalls, dailyCallCap, null, null, null),
                new CampaignRecurrence(recurrenceType, recurringDayOfMonth, cronExpression, autoResetTargets),
                null, null, lastRunAt, null, null, null, null, null, 0L);
    }
}
