package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;

import java.util.List;

public record UpdatePersonalNotificationSettingsRequest(
        @NotNull List<@Valid PersonalNotificationMatrixEntry> matrix
) {
}
