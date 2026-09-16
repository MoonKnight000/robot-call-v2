package uz.murodjon.robotcallv2.operator.application.dto;

import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Instant;

/** One operator on the supervisor's list: who, how to reach them, and what they are doing now. */
public record OperatorRow(
        long userId,
        String name,
        String extension,
        OperatorStatus status,
        String reason,
        Instant since,
        String channelId,
        int callsToday,
        boolean webrtc,
        String forwardNumber
) {
}
