package uz.murodjon.robotcallv2.notification.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

/** PUT /api/notifications/preferences body — one toggle at a time (UI-DESIGN §8.2). */
public record UpdatePreferenceRequest(@NotNull NotificationType type, boolean enabled) {
}
