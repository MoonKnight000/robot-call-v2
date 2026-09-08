package uz.murodjon.robotcallv2.aiagent.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * How hard the inbound cleaner works on the caller's leg before the recognizer
 * hears it ({@code TelephonyNoiseCanceller}).
 */
public enum NoiseCancellationMode {
    /** No noise cancellation applied. */
    OFF,

    /**
     * Reduces non-speech background noise such as traffic, fans, AC, humming,
     * typing, and environmental static while preserving natural caller voice.
     */
    BACKGROUND_NOISE_SUPPRESSION,

    /**
     * Emphasizes the primary speaker and aggressively suppresses competing speech,
     * background chatter, and room reverberation.
     */
    VOICE_ISOLATION;

    @JsonCreator
    public static NoiseCancellationMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return BACKGROUND_NOISE_SUPPRESSION;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("OFF") || normalized.equals("NONE") || normalized.equals("DISABLED")) {
            return OFF;
        }
        if (normalized.contains("ISOLAT")) {
            return VOICE_ISOLATION;
        }
        if (normalized.contains("SUPPRESS") || normalized.contains("BACKGROUND") || normalized.contains("NOISE")
                || normalized.contains("STANDARD")) {
            return BACKGROUND_NOISE_SUPPRESSION;
        }
        for (NoiseCancellationMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return BACKGROUND_NOISE_SUPPRESSION;
    }
}
