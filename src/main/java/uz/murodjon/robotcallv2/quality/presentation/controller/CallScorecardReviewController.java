package uz.murodjon.robotcallv2.quality.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewScorecardRequest;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Scored operator calls and their review inbox ({@code /api/call-scorecards}, docs/api/scorecards.md):
 * the calls the judge scored under the company's threshold, or with an upset client, wait in
 * {@code PENDING} for a supervisor to confirm or correct them.
 */
@RequestMapping("/api/call-scorecards")
public interface CallScorecardReviewController {

    /** Every scored call, or only those in {@code status} — {@code PENDING} is the inbox. */
    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<CallScorecard>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CallScorecardFilter filter);

    /**
     * Confirms the judge, or corrects some of its answers and re-scores the call. Any scored call
     * may be reviewed, flagged or not; a later review replaces an earlier one.
     */
    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @PostMapping("/{callId:\\d+}/review")
    ResponseEntity<ResponseData<CallScorecard>> review(
            @CurrentCompanyId long companyId,
            @PathVariable long callId,
            @Valid @RequestBody ReviewScorecardRequest request);
}
