package uz.murodjon.uysotvoice.live.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import uz.murodjon.uysotvoice.live.dto.LiveEventType;

/**
 * Live server-push channel (docs/API-REQUIREMENTS.md §0.7): KPI ticks, the live-call
 * list, per-call transcript lines, per-call audio levels, and notifications — see
 * {@link LiveEventType} for the {@code event:} names and their payloads.
 *
 * <p>The response is <strong>not</strong> wrapped in {@code ResponseData<T>} like every
 * other endpoint in this project. That envelope is JSON-object shaped and assumes one
 * response per request; an {@code EventSource} instead expects a raw
 * {@code text/event-stream} body it keeps reading from indefinitely, so wrapping it
 * would make the stream itself unparseable rather than just non-standard.
 *
 * <p>Authenticated the same way as every other endpoint ({@code X-Api-Key}); the
 * browser's {@code EventSource} API cannot set custom headers, so a frontend consuming
 * this will need a fetch-based SSE client rather than the built-in one.
 */
@RequestMapping("/api")
public interface LiveController {

    @GetMapping(value = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream();
}
