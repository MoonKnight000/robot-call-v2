package uz.murodjon.robotcallv2.siptrunk.application.dto;

import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.time.Instant;
import java.util.List;

/**
 * One row of GET/POST /api/sip-trunks... (ROADMAP B.3).
 */
public record SipTrunkRow(
        long id,
        String name,
        String pjsipEndpoint,
        String callerId,
        boolean managed,
        String host,
        int port,
        String sipUsername,
        SipTrunkTransport transport,
        List<String> codecs,
        boolean isDefault,
        boolean enabled,
        Instant createdAt
) {
    public static SipTrunkRow of(SipTrunk t) {
        return new SipTrunkRow(t.id(), t.name(), t.pjsipEndpoint(), t.callerId(), t.host() != null, t.host(),
                t.port(), t.sipUsername(), t.transport(), t.codecs(), t.isDefault(), t.enabled(), t.createdAt());
    }
}
