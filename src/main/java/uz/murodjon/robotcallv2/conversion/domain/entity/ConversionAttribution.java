package uz.murodjon.robotcallv2.conversion.domain.entity;

import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

import java.time.Instant;

/**
 * The call a conversion was credited to.
 *
 * <p>{@code attributionModel} and {@code windowHours} are copied here rather than read back
 * from the goal, so an attribution can still be explained after somebody retunes the goal —
 * the same reason a settled call carries the rate version it was charged at.
 */
public record ConversionAttribution(
        Long id,
        long conversionEventId,
        long companyId,
        Long campaignId,
        Long variantId,
        Long callAttemptId,
        AttributionModel attributionModel,
        int windowHours,
        Long attributedValueUzs,
        Instant computedAt
) {
    public static ConversionAttribution of(long conversionEventId, long companyId, AnsweredCall call,
                                           AttributionModel model, int windowHours, Long valueUzs) {
        return new ConversionAttribution(null, conversionEventId, companyId, call.campaignId(),
                call.variantId(), call.callAttemptId(), model, windowHours, valueUzs, null);
    }
}
