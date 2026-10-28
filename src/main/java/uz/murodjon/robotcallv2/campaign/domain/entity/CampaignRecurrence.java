package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;

/**
 * Whether and when a campaign runs again on its own.
 *
 * @param type             ONCE, DAILY, WEEKLY, MONTHLY or CRON; null reads as ONCE
 * @param dayOfMonth       the day a MONTHLY campaign runs on (1–31)
 * @param cronExpression   the schedule of a CRON campaign
 * @param autoResetTargets whether each run puts the targets back to PENDING
 */
public record CampaignRecurrence(
        RecurrenceType type,
        Integer dayOfMonth,
        String cronExpression,
        boolean autoResetTargets
) {
}
