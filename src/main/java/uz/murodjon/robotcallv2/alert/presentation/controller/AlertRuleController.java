package uz.murodjon.robotcallv2.alert.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.alert.application.dto.AlertRulePreviewResponse;
import uz.murodjon.robotcallv2.alert.application.dto.SaveAlertRuleRequest;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRuleFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Thresholds a company sets on its own calls. A breached rule sends an {@code ALERT_TRIGGERED}
 * notification — the panel bell, plus whichever channels (Telegram, email, webhook) the
 * company's notification settings route that type to.
 */
@RequestMapping("/api/alert-rules")
public interface AlertRuleController {

    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<AlertRule>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveAlertRuleRequest request);

    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_READ')")
    @PostMapping("/filter")
    ResponseEntity<ResponseData<PageableData<AlertRule>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody AlertRuleFilter filter);

    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AlertRule>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** Replaces the settings; the last value and last firing are kept. */
    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AlertRule>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveAlertRuleRequest request);

    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** The rule's metric right now and whether it would fire. Sends nothing, changes nothing. */
    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_READ')")
    @GetMapping("/{id:\\d+}/preview")
    ResponseEntity<ResponseData<AlertRulePreviewResponse>> preview(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
