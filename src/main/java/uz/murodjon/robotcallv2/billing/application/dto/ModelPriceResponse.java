package uz.murodjon.robotcallv2.billing.application.dto;

import java.time.Instant;

/**
 * One row of the per-model price list, before the markup, in UZS.
 *
 * @param model                the model id
 * @param inputPerMillionUzs   prompt tokens at the full rate, per million
 * @param outputPerMillionUzs  completion tokens, per million
 * @param cachedPerMillionUzs  cached prompt tokens, per million
 * @param realtimePerMinuteUzs speech-to-speech minutes, or null
 * @param note                 the admin's note
 * @param updatedAt            when the row was last changed
 */
public record ModelPriceResponse(
        String model,
        long inputPerMillionUzs,
        long outputPerMillionUzs,
        long cachedPerMillionUzs,
        Long realtimePerMinuteUzs,
        String note,
        Instant updatedAt
) {
}
