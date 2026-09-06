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
    public ResponseEntity<ResponseData<NotificationSettings>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(notificationSettingsUseCase.findByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<NotificationSettings>> update(long companyId,
                                                                      UpdateNotificationSettingsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(
                notificationSettingsUseCase.updateByCompanyId(companyId, request)));
    }
}
