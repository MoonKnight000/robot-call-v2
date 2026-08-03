package uz.murodjon.uysotvoice.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.notification.dto.NotificationSettings;
import uz.murodjon.uysotvoice.notification.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.uysotvoice.notification.service.NotificationSettingsService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class NotificationSettingsControllerImpl implements NotificationSettingsController {

    private final NotificationSettingsService service;

    public NotificationSettingsControllerImpl(NotificationSettingsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<NotificationSettings>> get() {
        return ResponseEntity.ok(ResponseData.ok(service.find()));
    }

    @Override
    public ResponseEntity<ResponseData<NotificationSettings>> update(UpdateNotificationSettingsRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(r)));
    }
}
