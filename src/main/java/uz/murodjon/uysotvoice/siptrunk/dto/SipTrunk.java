package uz.murodjon.uysotvoice.siptrunk.dto;

import java.time.Instant;

/** One row of {@code GET/POST /api/sip-trunks...} (ROADMAP B.3). */
public record SipTrunk(
        long id,
        String name,
        String pjsipEndpoint,
        String callerId,
        boolean isDefault,
        boolean enabled,
        Instant createdAt
) {
}
