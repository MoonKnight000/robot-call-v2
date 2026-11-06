package uz.murodjon.robotcallv2.quality.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.quality.application.dto.ScorecardSettingsResponse;
import uz.murodjon.robotcallv2.quality.application.dto.UpdateScorecardSettingsRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The company's own scorecard settings (docs/api/scorecards.md): the review threshold. It is
 * stored in the company config, whose other settings stay platform-only.
 */
@RequestMapping("/api/scorecard-settings")
public interface ScorecardSettingsController {

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_READ')")
    @GetMapping
    ResponseEntity<ResponseData<ScorecardSettingsResponse>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('QUALITY_REVIEW_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<ScorecardSettingsResponse>> update(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateScorecardSettingsRequest request);
}
