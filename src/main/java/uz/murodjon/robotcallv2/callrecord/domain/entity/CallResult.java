package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Pure domain record for post-call summary result. */
public record CallResult(
        long id,
        long callId,
        String summary,
        ReasonCode reasonCode,
        LocalDate promisedDate,
        BigDecimal promisedAmount,
        Sentiment sentiment,
        boolean needsFollowUp,
        String followUpNote,
        boolean escalated,
        String crmNoteId,
        Instant createdAt,
        int crmAttempts,
        String crmLastError,
        String outcome
) {

    /**
     * The post-call summary on its way to storage. Identity, {@code createdAt} and the
     * CRM retry bookkeeping belong to the storage layer, which fills them in itself.
     */
    public static CallResult summaryOf(long callId, String summary, ReasonCode reasonCode, LocalDate promisedDate,
                                       BigDecimal promisedAmount, Sentiment sentiment, boolean needsFollowUp,
                                       String followUpNote, boolean escalated, String crmNoteId, String outcome) {
        return new CallResult(0, callId, summary, reasonCode, promisedDate, promisedAmount, sentiment,
                needsFollowUp, followUpNote, escalated, crmNoteId, null, 0, null, outcome);
    }
}
