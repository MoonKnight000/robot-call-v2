package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * A finished call the outbox still has to summarize: the CRM client it was about, the
 * scenario the live call ran (for its outcome schema), how it ended, and the agent
 * whose analysis settings ({@code dataNeeded} / {@code dataEvaluation}) the summary asks for.
 */
public record PendingSummary(
        long callId,
        long clientId,
        long scenarioId,
        Disposition disposition,
        long agentId
) {
}
