package uz.murodjon.robotcallv2.report.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One call as a report line: the attempt joined to its target and result.
 *
 * @param callId       {@code call_attempt} id — what the detail and recording endpoints take
 * @param targetId     the target dialled
 * @param phone        number dialled
 * @param language     language the conversation ran in
 * @param startedAt    when media came up
 * @param endedAt      when the call finished (null while it is still running)
 * @param durationSec  length in seconds (null while running)
 * @param disposition  final outcome
 * @param hangupCause  Asterisk cause, which is what explains a NO_ANSWER (§8.6)
 * @param hasRecording whether audio is retrievable for this call
 * @param summary      the CRM note text, when a summary was produced
 * @param promisedDate date the client promised to pay, if any
 * @param promisedAmount amount promised, if any
 * @param crmNoteId    id the CRM gave the note; null means it has not been accepted yet —
 *                     the outbox is still retrying, or the CRM is off
 * @param clientName   from the target's {@code context_data.clientName} (backend-uchun-
 *                     talablar.md §2), set at CSV/API import time; {@code null} if that
 *                     field was never supplied for this target
 * @param campaignName the campaign this call's target belongs to
 * @param operatorName {@code app_user} who handled this call, if it was transferred to a
 *                     human and the answering endpoint matched a registered SIP
 *                     extension ({@code CallAttemptEntity#operatorUserId}); {@code null}
 *                     for every bot-only call
 */
public record CallRow(
        long callId,
        long targetId,
        String phone,
        String language,
        Instant startedAt,
        Instant endedAt,
        Integer durationSec,
        Disposition disposition,
        String hangupCause,
        boolean hasRecording,
        String summary,
        @JsonFormat(pattern = DateTimeProperties.DATE_PATTERN) LocalDate promisedDate,
        BigDecimal promisedAmount,
        Long crmNoteId,
        String clientName,
        String campaignName,
        String operatorName
) {
}

