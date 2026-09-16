package uz.murodjon.robotcallv2.missedcall.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;

/** Body of PUT /api/missed-calls/{id}. */
public record UpdateMissedCallRequest(
        @NotNull MissedCallStatus status,
        @Size(max = 2000) String note
) {
}
