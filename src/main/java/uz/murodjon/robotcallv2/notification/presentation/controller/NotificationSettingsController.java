package uz.murodjon.robotcallv2.notification.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Company-level notification channel x event matrix (§11 settings).
 */
@RequestMapping("/api/settings/notifications")
public interface NotificationSettingsController {

    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_READ')")
    @GetMapping
    ResponseEntity<ResponseData<NotificationSettings>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('NOTIFICATION_SETTINGS_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<NotificationSettings>> update(@CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request);
}
