package uz.murodjon.robotcallv2.notification.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.notification.application.dto.UpdatePreferenceRequest;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class NotificationControllerImpl implements NotificationController {

    private final NotificationUseCase notificationUseCase;

    public NotificationControllerImpl(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<Notification>>> list() {
        return ResponseEntity.ok(ResponseData.ok(notificationUseCase.list()));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> markRead(long id) {
        notificationUseCase.markRead(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> updatePreference(UpdatePreferenceRequest r) {
        notificationUseCase.setPreference(r.type(), r.enabled());
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
