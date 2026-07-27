package uz.murodjon.uysotvoice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps the exceptions the call/campaign API actually throws onto sensible statuses.
 * Without this a rejected phone number or a hung-up channel both surface as a bare
 * 500, which tells a client nothing about whether retrying could help.
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400: the caller sent something invalid
 *       (bad number, blank text, a playback path outside the recording directory).</li>
 *   <li>{@link IllegalStateException} → 409: the request was well-formed but the
 *       system is not in a state to serve it (no ARI connection, call already gone,
 *       TTS disabled).</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        log.debug("Rejected request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        log.warn("Request could not be served: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
