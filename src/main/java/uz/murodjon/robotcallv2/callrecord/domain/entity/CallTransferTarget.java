package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.callrecord.domain.enums.TransferTargetType;

/**
 * Where a call is being handed on to.
 *
 * @param targetId the queue or user id; null for EXTERNAL
 * @param number   the phone number for EXTERNAL; null otherwise
 */
public record CallTransferTarget(
        TransferTargetType type,
        Long targetId,
        String number
) {
}
