package uz.murodjon.robotcallv2.voicemail.domain.entity;

import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;

import java.time.Instant;

/**
 * One message a caller left.
 *
 * @param callAttemptId the call it was left on — its summary and recording are there
 * @param storedFileId  the message audio; null if storage was down when it was recorded
 * @param transcript    what the recognizer heard; null when the box does not transcribe
 */
public record VoicemailMessage(
        long id,
        long companyId,
        long boxId,
        Long callAttemptId,
        String callerPhone,
        Long storedFileId,
        int durationSec,
        String transcript,
        VoicemailStatus status,
        Instant createdAt
) {
}
