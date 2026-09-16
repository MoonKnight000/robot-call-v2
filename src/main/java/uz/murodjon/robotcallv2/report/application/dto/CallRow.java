package uz.murodjon.robotcallv2.report.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One line of the call list ({@code POST /api/reports/calls/list}) and of its CSV export.
 * The fields are those of {@code ReportedCall}; see docs/api/reports.md for their meaning.
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
