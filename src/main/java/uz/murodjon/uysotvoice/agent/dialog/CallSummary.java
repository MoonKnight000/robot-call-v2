package uz.murodjon.uysotvoice.agent.dialog;

import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;
import uz.murodjon.uysotvoice.shared.dialog.Sentiment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured summary produced by a single LLM call over the full transcript after
 * the conversation ends (PROJECT.md §4.3). Written to {@code call_result} and to the
 * CRM note. Nullable fields are left null when the call yielded no such information.
 *
 * @param summary        2–3 sentence CRM note
 * @param reasonCode     why the debt is unpaid (nullable)
 * @param promisedDate   promised payment date (nullable)
 * @param promisedAmount promised amount (nullable)
 * @param sentiment      overall client sentiment
 * @param needsFollowUp  whether a follow-up call is warranted
 * @param followUpNote   note for the follow-up (nullable)
 */
public record CallSummary(
        String summary,
        ReasonCode reasonCode,
        LocalDate promisedDate,
        BigDecimal promisedAmount,
        Sentiment sentiment,
        boolean needsFollowUp,
        String followUpNote
) {
}
