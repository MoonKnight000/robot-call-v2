package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code PUT /api/profile/notifications} body — whole-grid replace, same as company settings. */
public record UpdatePersonalNotificationSettingsRequest(
        @NotNull @Valid List<PersonalNotificationMatrixEntry> matrix
) {
}
