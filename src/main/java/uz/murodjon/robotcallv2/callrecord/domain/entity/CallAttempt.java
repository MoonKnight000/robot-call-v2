package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/** Pure domain record for a call attempt. */
public record CallAttempt(
        long id,
        long companyId,
        long targetId,
        String phone,
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
        Long inboundRouteId,
        Long operatorUserId
) {

    /**
     * A call that is being dialled right now. It is written before anyone picks up, so
     * that a call nobody answered — and one the carrier refused outright — still leaves a
     * row; {@code answeredAt} stays null until it is answered.
     */
    public static CallAttempt starting(long companyId, long targetId, String channelId, String phone,
                                       String language, Long inboundRouteId) {
        return new CallAttempt(0, companyId, targetId, phone, null, channelId, language,
                null, null, null, null, null, null, null, null, null, 0, inboundRouteId, null);
    }

    /**
     * A call that never reached the trunk. The target's attempt counter was already spent
     * when the dialer claimed it, so the row is written closed — there is no channel that
     * could close it later.
     */
    public static CallAttempt unplaced(long companyId, long targetId, String phone, String language,
                                       Disposition disposition, String reason) {
        return new CallAttempt(0, companyId, targetId, phone, null, null, language,
                null, null, null, 0, disposition, null, null, reason, null, 0, null, null);
    }
}
