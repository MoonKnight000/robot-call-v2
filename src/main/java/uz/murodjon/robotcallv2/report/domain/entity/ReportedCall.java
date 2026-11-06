package uz.murodjon.robotcallv2.report.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One call as the reports see it: the attempt joined to its target and result.
 *
 * @param callId       {@code call_attempt} id — what the detail and recording endpoints take
 * @param targetId     the target dialled, or 0 for a call no campaign placed
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
 * @param clientName   from the target's {@code context_data.clientName}, set at CSV/API
 *                     import time; {@code null} if that field was never supplied
 * @param campaignName the campaign this call's target belongs to
 * @param operatorName {@code app_user} who handled this call, if it was transferred to a
 *                     human; {@code null} for every bot-only call
 * @param aiAgentId    the AI agent that ran the call; null for an operator-only call or a deleted agent
 * @param agentName    that agent's name
 * @param inbound      true for a call that came in through an inbound route
 * @param sentiment    the caller's mood as the post-call summary read it; null until summarised
 * @param qualityScore the AI quality judge's 0–100 score; null when the call was not judged
 * @param costUzs      what the call was billed, once settled; null before or without billing
 * @param avgTurnLatencyMs mean time from the caller finishing to the agent's first audio; null without AI turns
 * @param agentVersion the published agent version the call spoke with; null for a draft (web test) or no agent
 */
public record ReportedCall(
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
        LocalDate promisedDate,
        BigDecimal promisedAmount,
        String crmNoteId,
        String clientName,
        String campaignName,
        String operatorName,
        Long aiAgentId,
        String agentName,
        boolean inbound,
        Sentiment sentiment,
        Integer qualityScore,
        Long costUzs,
        Integer avgTurnLatencyMs,
        Integer agentVersion
) {
}
