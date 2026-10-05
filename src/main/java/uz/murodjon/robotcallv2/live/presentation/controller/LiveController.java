package uz.murodjon.robotcallv2.live.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;

/**
 * Live server-push channel (docs/API-REQUIREMENTS.md §0.7). A stream only ever carries the
 * events of the company the token belongs to.
 */
@RequestMapping("/api")
public interface LiveController {

    @PreAuthorize("hasAuthority('LIVE_READ')")
    @GetMapping(value = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@CurrentCompanyId long companyId);
}
