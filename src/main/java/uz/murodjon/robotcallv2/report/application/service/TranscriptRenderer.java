package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.report.domain.entity.CallDetail;
import uz.murodjon.robotcallv2.report.domain.entity.TranscriptLine;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Renders the {@code transcript.txt} body for {@code GET
 * /api/reports/calls/{callId}/transcript.txt} (§10.5 "TXT yuklab olish") — one
 * {@code [hh:mm:ss] role: text} line per {@link CallDetail#transcript()} entry.
 */
@Component
public class TranscriptRenderer {

    private static final String TEXT = "text/plain; charset=UTF-8";

    public String contentType() {
        return TEXT;
    }

    public byte[] renderBytes(CallDetail detail) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptLine line : detail.transcript()) {
            sb.append('[').append(formatOffset(line.tsOffsetMs())).append("] ")
                    .append(line.role()).append(": ").append(line.text()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatOffset(int tsOffsetMs) {
        Duration duration = Duration.ofMillis(Math.max(0, tsOffsetMs));
        return "%02d:%02d:%02d".formatted(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }
}
