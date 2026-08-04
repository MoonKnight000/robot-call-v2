package uz.murodjon.uysotvoice.siptrunk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;

/**
 * As {@link CreateSipTrunkRequest} (same manual-vs-managed mode split), plus {@code
 * enabled}. {@code isDefault} is not editable here — see {@code POST
 * /api/sip-trunks/{id}/default}.
 *
 * @param sipPassword managed mode only, and optional even then — blank/omitted keeps
 *                     the trunk's current encrypted password (so renaming a trunk, or
 *                     flipping {@code enabled}, does not force re-entering the secret).
 *                     Required only the first time a trunk switches into managed mode.
 */
public record UpdateSipTrunkRequest(
        @NotBlank String name,
        String pjsipEndpoint,
        String host,
        Integer port,
        String sipUsername,
        String sipPassword,
        SipTrunkTransport transport,
        String callerId,
        @NotNull Boolean enabled
) {
}
