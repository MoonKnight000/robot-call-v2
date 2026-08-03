package uz.murodjon.uysotvoice.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.notification.dto.Notification;
import uz.murodjon.uysotvoice.notification.dto.UpdatePreferenceRequest;
import uz.murodjon.uysotvoice.notification.service.NotificationService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class NotificationControllerImpl implements NotificationController {

    private final NotificationService service;

    public NotificationControllerImpl(NotificationService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<List<Notification>>> list() {
        return ResponseEntity.ok(ResponseData.ok(service.list()));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> markRead(long id) {
        service.markRead(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> updatePreference(UpdatePreferenceRequest r) {
        service.setPreference(r.type(), r.enabled());
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
