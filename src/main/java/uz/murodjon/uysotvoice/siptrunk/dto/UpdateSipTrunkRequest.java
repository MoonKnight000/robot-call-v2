package uz.murodjon.uysotvoice.siptrunk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code isDefault} is not editable here — see {@code POST /api/sip-trunks/{id}/default}. */
public record UpdateSipTrunkRequest(
        @NotBlank String name,
        @NotBlank String pjsipEndpoint,
        String callerId,
        @NotNull Boolean enabled
) {
}
