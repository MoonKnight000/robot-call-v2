package uz.murodjon.robotcallv2.callrecord.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallReanalysisPreviewResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.CreateCallReanalysisRequest;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisFilter;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Analysing an agent's past calls again with its current {@code dataNeeded} /
 * {@code dataEvaluation} settings. Only those keys of each call's stored outcome change;
 * nothing the call set off (CRM note, callback, post-call webhooks) is repeated. Paid: each
 * call's tokens are charged.
 */
@RequestMapping("/api/call-reanalyses")
public interface CallReanalysisController {

    /** How many calls the request covers and roughly what it would cost. Queues and charges nothing. */
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping("/preview")
    ResponseEntity<ResponseData<CallReanalysisPreviewResponse>> preview(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateCallReanalysisRequest request);

    /** Queues the job; poll {@code GET /{id}} for its progress. One active job per company. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallReanalysisJob>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateCallReanalysisRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping("/filter")
    ResponseEntity<ResponseData<PageableData<CallReanalysisJob>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CallReanalysisFilter filter);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<CallReanalysisJob>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** Stops a queued or running job; the calls already done keep their new analysis. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/cancel")
    ResponseEntity<ResponseData<CallReanalysisJob>> cancel(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
