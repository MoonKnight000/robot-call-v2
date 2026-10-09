package uz.murodjon.robotcallv2.quality.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.quality.application.dto.SaveScorecardCriterionRequest;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterionFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The company's scorecard criteria (docs/api/scorecards.md): what every operator call is
 * marked on after it ends.
 */
@RequestMapping("/api/scorecard-criteria")
public interface ScorecardCriterionController {

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<ScorecardCriterion>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveScorecardCriterionRequest request);

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<ScorecardCriterion>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ScorecardCriterionFilter filter);

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<ScorecardCriterion>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<ScorecardCriterion>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveScorecardCriterionRequest request);

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
