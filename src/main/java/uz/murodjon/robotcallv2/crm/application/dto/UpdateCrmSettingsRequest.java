package uz.murodjon.robotcallv2.crm.application.dto;

import jakarta.validation.constraints.Positive;

/** PUT /api/settings/crm — every field optional; {@code null} clears it back to the platform default. */
public record UpdateCrmSettingsRequest(
        @Positive Integer aiEmployeeId,
        @Positive Integer meetingBookedStatusId,
        @Positive Integer promiseToPayStatusId,
        @Positive Integer notInterestedStatusId,
        @Positive Integer callbackStatusId,
        @Positive Integer newLeadStatusId
) {
}
