package uz.murodjon.robotcallv2.conversion.domain.entity;

import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

import java.time.Instant;

/**
 * What a company counts as a call having worked, and how long after a call it still counts.
 *
 * <p>Company-scoped rather than per campaign, because the system that posts the event
 * knows a customer paid — not which campaign called them. Working that out is attribution's
 * job, and it is what lets one CRM webhook serve every campaign a company runs.
 *
 * @param goalKey                what the posting system names it: {@code payment}, {@code appointment}
 * @param attributionWindowHours how long after a call a conversion may still be credited to it
 * @param attributionModel       which call in the window gets the credit
 * @param enabled                a goal switched off rejects its events instead of attributing them
 */
public record ConversionGoal(
        Long id,
        long companyId,
        String goalKey,
        String name,
        int attributionWindowHours,
        AttributionModel attributionModel,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
