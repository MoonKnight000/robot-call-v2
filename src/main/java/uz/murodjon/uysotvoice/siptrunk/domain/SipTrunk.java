package uz.murodjon.uysotvoice.siptrunk.domain;

import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;

import java.time.Instant;

/**
 * The domain model of {@code sip_trunk} (ROADMAP B.3) — carries the encrypted password,
 * so it never reaches a controller directly; API responses go through {@link
 * uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkRow}. Used internally by {@code
 * PjsipConfigWriter}, which needs the real credentials to render Asterisk's config.
 */
public record SipTrunk(
        long id,
        long companyId,
        String name,
        String pjsipEndpoint,
        String callerId,
        String host,
        int port,
        String sipUsername,
        String sipPasswordEnc,
        SipTrunkTransport transport,
        boolean isDefault,
        boolean enabled,
        Instant createdAt
) {
}
