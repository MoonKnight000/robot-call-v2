package uz.murodjon.robotcallv2.siptrunk.domain.entity;

import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.time.Instant;
import java.util.List;

/**
 * Domain model of sip_trunk (ROADMAP B.3).
 * Supports HD voice and custom audio codecs per trunk (e.g. ["g722", "opus", "ulaw", "alaw"]).
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
        List<String> codecs,
        boolean isDefault,
        boolean enabled,
        Instant createdAt
) {
}
