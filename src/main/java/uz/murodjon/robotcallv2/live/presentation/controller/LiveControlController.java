package uz.murodjon.robotcallv2.live.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Live call supervision: whisper guidance the customer never hears, and handing the
 * channel over to a human operator.
 */
@RequestMapping("/api/calls/live")
public interface LiveControlController {

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/whisper")
    ResponseEntity<ResponseData<LiveControlResponse>> whisper(@PathVariable String channelId,
                                                             @Valid @RequestBody WhisperRequest request);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/takeover")
    ResponseEntity<ResponseData<LiveControlResponse>> takeover(
            @PathVariable String channelId,
            @RequestBody(required = false) TakeoverRequest request);
}
