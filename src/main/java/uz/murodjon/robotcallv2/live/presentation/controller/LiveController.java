package uz.murodjon.robotcallv2.live.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live server-push channel (docs/API-REQUIREMENTS.md §0.7).
 */
@Tag(name = "Live Stream", description = "Realtime SSE streaming events (call lifecycle, live audio levels, transcripts, KPI)")
@RequestMapping("/api")
public interface LiveController {

    @Operation(
            summary = "Subscribe to live SSE event stream",
            description = "Subscribes the client to real-time Server-Sent Events (SSE). Receives live system events including: "
                    + "connected (initial handshake), KPI, LIVE_CALLS, TRANSCRIPT, AUDIO_LEVEL, "
                    + "NOTIFICATION, and heartbeat pings (:ping)."
    )
    @GetMapping(value = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream();
}
