package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;

/**
 * {@link CampaignRecurrence} as the campaign endpoints read and write it.
 *
 * @param type             ONCE (default), DAILY, WEEKLY, MONTHLY, CRON. Anything but ONCE re-runs
 *                         the campaign on its own; pair it with a target source
 *                         ({@code PUT /api/campaigns/{id}/target-source}) to have each run dial a
 *                         freshly fetched list instead of the same one
 * @param dayOfMonth       the day of the month for MONTHLY (1–31)
 * @param cronExpression   custom cron expression for CRON
 * @param autoResetTargets whether targets are reset to PENDING when a run starts
 */
public record CampaignRecurrenceDto(
        RecurrenceType type,
        @Min(1) @Max(31) Integer dayOfMonth,
        String cronExpression,
        Boolean autoResetTargets
) {

    /** A campaign read before recurrence existed has no type; it runs once. */
    public static CampaignRecurrenceDto of(CampaignRecurrence recurrence) {
        return new CampaignRecurrenceDto(recurrence.type() != null ? recurrence.type() : RecurrenceType.ONCE,
                recurrence.dayOfMonth(), recurrence.cronExpression(), recurrence.autoResetTargets());
    }

    public RecurrenceType typeOrDefault() {
        return type != null ? type : RecurrenceType.ONCE;
    }

    public boolean autoResetTargetsOrDefault() {
        return autoResetTargets != null && autoResetTargets;
    }
}
