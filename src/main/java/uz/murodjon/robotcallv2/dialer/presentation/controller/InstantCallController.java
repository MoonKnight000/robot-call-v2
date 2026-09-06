package uz.murodjon.robotcallv2.dialer.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Instant high-priority outbound calls: a web lead or CRM hook jumps the dial queue
 * instead of waiting for the campaign's next dispatch cycle (PROJECT.md §5.2).
 */
@RequestMapping("/api/calls/instant")
public interface InstantCallController {

    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<InstantCallResponse>> trigger(@CurrentCompanyId long companyId,
            @Valid @RequestBody InstantCallRequest request);
}
