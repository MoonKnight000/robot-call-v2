package uz.murodjon.robotcallv2.voicemail.domain.entity;

import java.time.Instant;

/**
 * Where a caller can leave a message (docs/INBOUND-PBX-PLAN.md §8.1).
 *
 * @param greetingText   spoken with TTS before the beep, unless {@code greetingFileId} is set
 * @param greetingFileId a recorded greeting (stored_file, WAV) played instead
 * @param maxSeconds     the longest message recorded
 * @param transcribe     run the message through STT while it is recorded
 */
public record VoicemailBox(
        long id,
        long companyId,
        String name,
        String greetingText,
        Long greetingFileId,
        int maxSeconds,
        boolean transcribe,
        boolean enabled,
        Instant createdAt
) {
}
