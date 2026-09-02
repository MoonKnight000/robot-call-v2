package uz.murodjon.robotcallv2.notification.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.notification.application.dto.UpdatePreferenceRequest;
import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/** Topbar bell (UI-DESIGN §9) and its preferences submenu (§8.2). */
@RequestMapping("/api/notifications")
public interface NotificationController {

    @GetMapping
    ResponseEntity<ResponseData<List<Notification>>> list();

    @PostMapping("/{id}/read")
    ResponseEntity<ResponseData<Void>> markRead(@PathVariable long id);

    @PutMapping("/preferences")
    ResponseEntity<ResponseData<Void>> updatePreference(@Valid @RequestBody UpdatePreferenceRequest r);
}
