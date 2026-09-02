package uz.murodjon.robotcallv2.siptrunk.application.dto;

import java.time.Instant;

/**
 * Live status report for a SIP trunk / phone registration (Asterisk AMI / PJSIP state).
 */
public record SipTrunkStatus(
        long id,
        String name,
        String pjsipEndpoint,
        boolean managed,
        boolean enabled,
        boolean isDefault,
        String status,
        boolean isOnline,
        String details,
        Instant checkedAt
) {
}
