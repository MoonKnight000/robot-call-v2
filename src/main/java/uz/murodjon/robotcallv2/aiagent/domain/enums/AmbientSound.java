package uz.murodjon.robotcallv2.aiagent.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * Ambient background soundscape types mixed into outbound audio at ~ -38dB to -43dB —
 * roughly 20 dB under the bot's own voice, the distance a real room sits at
 * ({@code AmbientSoundGenerator}).
 *
 * <p>{@link #KEYBOARD_TYPING} is the odd one out: it is not a room the bot is calling
 * from but a thing the bot is doing, so it is played only while a reply is being
 * composed rather than for the whole call.
 */
public enum AmbientSound {
    OFF,
    OFFICE,
    CALL_CENTER,
    NATURAL_LINE,
    CAFE,
    KEYBOARD_TYPING;

    @JsonCreator
    public static AmbientSound fromString(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.contains("KEYBOARD") || normalized.contains("TYPING")) {
            return KEYBOARD_TYPING;
        }
        if (normalized.contains("CALL_CENTER") || normalized.contains("CALLCENTER")) {
            return CALL_CENTER;
        }
        if (normalized.contains("OFFICE")) {
            return OFFICE;
        }
        if (normalized.contains("NATURAL") || normalized.contains("LINE")) {
            return NATURAL_LINE;
        }
        if (normalized.contains("CAFE") || normalized.contains("COFFEE")) {
            return CAFE;
        }
        for (AmbientSound sound : values()) {
            if (sound.name().equals(normalized)) {
                return sound;
            }
        }
        return OFF;
    }
}
