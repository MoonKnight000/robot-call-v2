package uz.murodjon.uysotvoice.notification.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.notification.dto.NotificationSettings;
import uz.murodjon.uysotvoice.notification.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Company-level notification channel x event matrix (§11 settings) — scoped to the
 * caller's own company via {@code CurrentCompany} (JWT), not a path id. ADMIN-only
 * ({@code SecurityConfig}). Not to be confused with {@code NotificationController}
 * (the per-user topbar bell + its per-user preference toggle).
 */
@RequestMapping("/api/settings/notifications")
public interface NotificationSettingsController {

    @GetMapping
    ResponseEntity<ResponseData<NotificationSettings>> get();

    @PutMapping
    ResponseEntity<ResponseData<NotificationSettings>> update(@Valid @RequestBody UpdateNotificationSettingsRequest r);
}
