package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.callrecord.domain.enums.TransferTargetType;

/**
 * Optional body of POST /api/calls/{channelId}/transfer and of .../consult. Without a body
 * a transfer goes where the agent's settings say (its queue, or the configured operator).
 *
 * @param targetId the queue or user id; null for EXTERNAL
 * @param number   the phone number for EXTERNAL
 */
public record TransferCallRequest(
        TransferTargetType type,
        Long targetId,
        String number
) {
}
