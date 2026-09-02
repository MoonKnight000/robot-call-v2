package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/** Pure domain record for a call attempt. */
public record CallAttempt(
        long id,
        long targetId,
        String sipCallId,
        String asteriskChannel,
        String language,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt,
        Integer durationSec,
        Disposition disposition,
        String hangupCause,
        Long recordingFileId,
        String errorMessage,
        Instant createdAt,
        int finalizeAttempts,
        long companyId,
        Long inboundRouteId,
        Long operatorUserId
) {
}
