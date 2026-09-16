package uz.murodjon.robotcallv2.operator.domain.entity;

import java.time.Instant;

/**
 * The SIP endpoint an operator's phone signs in as (operator_sip_account).
 *
 * @param endpointName   the PJSIP endpoint and SIP username, {@code op-<userId>}
 * @param passwordEnc    the SIP password, encrypted at rest; decrypted only to render the
 *                       Asterisk config and to hand the operator their own credentials
 * @param webrtc         true for the browser softphone, false for a SIP phone over UDP
 * @param forwardNumber  the operator's mobile, rung over the trunk when the softphone is offline
 */
public record OperatorSipAccount(
        long userId,
        long companyId,
        String endpointName,
        String passwordEnc,
        boolean webrtc,
        String forwardNumber,
        Instant createdAt,
        Instant updatedAt
) {

    /** The endpoint name for a user — stable, unique across companies, and free of anything the user typed. */
    public static String endpointNameFor(long userId) {
        return "op-" + userId;
    }
}
