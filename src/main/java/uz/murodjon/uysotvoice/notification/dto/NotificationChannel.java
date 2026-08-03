package uz.murodjon.uysotvoice.notification.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.notification.enums.NotificationChannelType;

/** One row of the channel list in {@code GET/PUT /api/settings/notifications} (§11). */
public record NotificationChannel(
        @NotNull NotificationChannelType channel,
        String target,
        boolean enabled
) {
}
