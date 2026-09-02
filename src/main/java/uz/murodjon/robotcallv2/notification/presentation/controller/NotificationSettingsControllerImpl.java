package uz.murodjon.robotcallv2.notification.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationSettingsUseCase;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class NotificationSettingsControllerImpl implements NotificationSettingsController {

    private final NotificationSettingsUseCase notificationSettingsUseCase;

    public NotificationSettingsControllerImpl(NotificationSettingsUseCase notificationSettingsUseCase) {
        this.notificationSettingsUseCase = notificationSettingsUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<NotificationSettings>> get() {
        return ResponseEntity.ok(ResponseData.ok(notificationSettingsUseCase.find()));
    }

    @Override
    public ResponseEntity<ResponseData<NotificationSettings>> update(UpdateNotificationSettingsRequest r) {
        return ResponseEntity.ok(ResponseData.ok(notificationSettingsUseCase.update(r)));
    }
}
