package uz.murodjon.uysotvoice.notification.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.notification.enums.NotificationType;

/** {@code PUT /api/notifications/preferences} body — one toggle at a time (UI-DESIGN §8.2). */
public record UpdatePreferenceRequest(@NotNull NotificationType type, boolean enabled) {
}
