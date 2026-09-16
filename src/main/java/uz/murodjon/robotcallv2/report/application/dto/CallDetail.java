package uz.murodjon.robotcallv2.report.application.dto;

import uz.murodjon.robotcallv2.report.domain.entity.CallTechnicalMetrics;
import uz.murodjon.robotcallv2.report.domain.entity.TranscriptLine;
import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.List;

/**
 * {@code GET /api/reports/calls/{callId}}: the call's line, its transcript, what the
 * summary made of it, and the technical tab. The fields are those of {@code CallReport}.
 */
public record CallDetail(
        CallRow call,
        List<TranscriptLine> transcript,
        ReasonCode reasonCode,
        Sentiment sentiment,
        boolean needsFollowUp,
        String followUpNote,
        boolean escalated,
        String errorMessage,
        CallTechnicalMetrics technical
) {
}
