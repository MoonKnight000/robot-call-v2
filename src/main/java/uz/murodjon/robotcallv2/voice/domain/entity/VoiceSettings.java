package uz.murodjon.robotcallv2.voice.domain.entity;

import java.time.Instant;

/**
 * A company's TTS tuning overrides.
 */
public record VoiceSettings(
        long companyId,
        Double speed,
        Double pitch,
        Instant createdAt
) {
    public static VoiceSettings overrides(Double speed, Double pitch) {
        return new VoiceSettings(0L, speed, pitch, null);
    }
}
