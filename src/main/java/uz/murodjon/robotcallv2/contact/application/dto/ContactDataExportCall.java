package uz.murodjon.robotcallv2.contact.application.dto;

import java.time.Instant;

/** One call in a data-subject export, with its full transcript. */
public record ContactDataExportCall(
        long callId,
        Instant startedAt,
        Integer durationSec,
        String disposition,
        String summary,
        String transcript
) {
}
