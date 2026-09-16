package uz.murodjon.robotcallv2.operator.application.dto;

import java.time.Instant;

/** An operator's SIP account as the admin screen shows it — without the password. */
public record OperatorSipAccountRow(
        long userId,
        String name,
        String extension,
        String endpointName,
        boolean webrtc,
        String forwardNumber,
        Instant createdAt
) {
}
