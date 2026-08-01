package uz.murodjon.uysotvoice.report.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.report.dto.CallDetail;
import uz.murodjon.uysotvoice.report.dto.TranscriptLine;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Builds the {@code transcript.txt} download for {@code GET
 * /api/reports/calls/{callId}/transcript.txt} (§10.5 "TXT yuklab olish"). Same
 * two-layer split as {@link RecordingResponseFactory}: the service already has the
 * data ({@link CallDetail#transcript()}), only the text format and HTTP headers are
 * a web concern.
 */
@Component
public class TranscriptResponseFactory {

    private static final MediaType TEXT = MediaType.parseMediaType("text/plain; charset=UTF-8");

    public ResponseEntity<Resource> toResponse(long callId, CallDetail detail) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptLine line : detail.transcript()) {
            sb.append('[').append(formatOffset(line.tsOffsetMs())).append("] ")
                    .append(line.role()).append(": ").append(line.text()).append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(TEXT)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"call-" + callId + "-transcript.txt\"")
                .body(new ByteArrayResource(body));
    }

    private static String formatOffset(int tsOffsetMs) {
        Duration d = Duration.ofMillis(Math.max(0, tsOffsetMs));
        return "%02d:%02d:%02d".formatted(d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}
