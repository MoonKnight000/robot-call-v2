package uz.murodjon.robotcallv2.conversion.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionGoalRequest;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionResultResponse;
import uz.murodjon.robotcallv2.conversion.application.dto.ReportConversionRequest;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * What a call was actually worth: a company's own systems report that a customer paid,
 * booked or renewed, and the platform works out which call earned it.
 *
 * <p>It exists because a disposition is not an outcome. The bot recording
 * {@code PROMISE_TO_PAY} says the caller agreed to pay, not that they did — so a variant
 * that is better at extracting promises has always looked like the winner. These endpoints
 * are what let the A/B report be judged on money instead.
 *
 * <p>{@link #reportConversion} is the one an integration calls, usually with an
 * {@code X-Api-Key} ([api-keys.md]) rather than a person's token. It is idempotent on
 * {@code dedupeKey}: a retried webhook gets back the same answer and changes nothing.
 */
@RequestMapping("/api/conversions")
public interface ConversionController {

    /** The goals this company counts, with their attribution windows. */
    @GetMapping("/goals")
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    ResponseEntity<ResponseData<List<ConversionGoal>>> goals(@CurrentCompanyId long companyId);

    /** Creates the goal, or updates the one already under that {@code goalKey}. */
    @PutMapping("/goals")
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    ResponseEntity<ResponseData<ConversionGoal>> upsertGoal(@CurrentCompanyId long companyId,
                                                           @Valid @RequestBody ConversionGoalRequest request);

    /**
     * Switches a goal off. The row and everything attributed under it are kept — a report
     * that cannot name its own goal is worse than one naming a goal nobody posts to.
     */
    @DeleteMapping("/goals/{id}")
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    ResponseEntity<ResponseData<Void>> deleteGoal(@CurrentCompanyId long companyId, @PathVariable long id);

    /**
     * Reports one thing that happened, and answers which call was credited for it.
     *
     * <p>The answer is deliberate: a poster whose number matched nothing learns so
     * immediately. A webhook that always says 200 and quietly drops half its events is how
     * a campaign's numbers end up wrong with nobody noticing.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    ResponseEntity<ResponseData<ConversionResultResponse>> reportConversion(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ReportConversionRequest request);
}
