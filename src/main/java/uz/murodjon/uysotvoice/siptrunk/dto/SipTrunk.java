package uz.murodjon.uysotvoice.siptrunk.dto;

import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;

import java.time.Instant;

/**
 * One row of {@code GET/POST /api/sip-trunks...} (ROADMAP B.3). Never carries the
 * password, even encrypted — {@code sipUsername}/{@code host}/{@code port}/{@code
 * transport} are {@code null} for a manual-mode trunk (report #7).
 *
 * @param managed {@code true} if the app registers this trunk itself ({@code host} set);
 *                {@code false} if {@code pjsipEndpoint} references a hand-configured one
 */
public record SipTrunk(
        long id,
        String name,
        String pjsipEndpoint,
        String callerId,
        boolean managed,
        String host,
        int port,
        String sipUsername,
        SipTrunkTransport transport,
        boolean isDefault,
        boolean enabled,
        Instant createdAt
) {
}
