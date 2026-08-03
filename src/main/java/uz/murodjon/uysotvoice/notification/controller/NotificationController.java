package uz.murodjon.uysotvoice.notification.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.notification.dto.Notification;
import uz.murodjon.uysotvoice.notification.dto.UpdatePreferenceRequest;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

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
