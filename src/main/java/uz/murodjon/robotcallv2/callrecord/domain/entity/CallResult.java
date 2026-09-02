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
        Long crmNoteId,
        Instant createdAt,
        int crmAttempts,
        String crmLastError,
        String outcome
) {
}
