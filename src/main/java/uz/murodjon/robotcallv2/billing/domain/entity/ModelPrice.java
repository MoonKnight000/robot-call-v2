package uz.murodjon.robotcallv2.billing.domain.entity;

import java.time.Instant;

/**
 * What one model costs the company, before the markup, in UZS.
 *
 * @param model                the model id, lower-case ({@code gemini-2.5-flash})
 * @param llm                  its token prices: input (prompt), output (completion), cached prompt
 * @param realtimePerMinuteUzs a speech-to-speech engine's price per minute, or null when the
 *                             model is not one (or billing.yml's realtime rate applies)
 * @param note                 free text for the admin: the source of the price, the date it was checked
 * @param updatedAt            storage's to stamp
 */
public record ModelPrice(
        String model,
        BillingLlmRates llm,
        Long realtimePerMinuteUzs,
        String note,
        Instant updatedAt
) {
}
