package uz.murodjon.robotcallv2.billing.application.dto;

/**
 * The price list in force now, as the company pays it (markup included), in UZS.
 *
 * @param version                 the price list's version, stamped on every call charged against it
 * @param inputPerMillionUzs      a million prompt tokens at the full rate
 * @param outputPerMillionUzs     a million completion tokens
 * @param cachedPerMillionUzs     a million cached prompt tokens
 * @param sttPerMinuteUzs         a minute of recognition
 * @param ttsPer1kCharsUzs        a thousand synthesised characters
 * @param realtimePerMinuteUzs    a minute on a speech-to-speech engine
 * @param telephonyPerMinuteUzs   a connected minute on the line
 * @param platformFeePerMinuteUzs the platform's own fee per connected minute
 */
public record CostRatesDto(
        String version,
        long inputPerMillionUzs,
        long outputPerMillionUzs,
        long cachedPerMillionUzs,
        long sttPerMinuteUzs,
        long ttsPer1kCharsUzs,
        long realtimePerMinuteUzs,
        long telephonyPerMinuteUzs,
        long platformFeePerMinuteUzs
) {
}
