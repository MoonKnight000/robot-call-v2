package uz.murodjon.robotcallv2.quality.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/** An operator call's scorecard (docs/api/scorecards.md); 404 until the call has been scored. */
@RequestMapping("/api/calls/{callId}/scorecard")
public interface CallScorecardController {

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @GetMapping
    ResponseEntity<ResponseData<CallScorecard>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long callId);
}
