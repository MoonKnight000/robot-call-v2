package uz.murodjon.robotcallv2.billing.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/platform/model-prices/{model}} — one model's prices before the markup, in UZS.
 *
 * @param inputPerMillionUzs   a million prompt tokens at the full rate
 * @param outputPerMillionUzs  a million completion tokens (thinking included)
 * @param cachedPerMillionUzs  a million prompt tokens served from the provider's cache
 * @param realtimePerMinuteUzs a minute on this speech-to-speech engine; null when it is not one
 * @param note                 where the price came from, for the next admin
 */
public record UpsertModelPriceRequest(
        @NotNull @PositiveOrZero Long inputPerMillionUzs,
        @NotNull @PositiveOrZero Long outputPerMillionUzs,
        @NotNull @PositiveOrZero Long cachedPerMillionUzs,
        @PositiveOrZero Long realtimePerMinuteUzs,
        @Size(max = 255) String note
) {
}
