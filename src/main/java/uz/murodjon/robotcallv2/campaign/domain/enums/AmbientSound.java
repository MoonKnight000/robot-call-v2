package uz.murodjon.robotcallv2.campaign.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * Ambient background soundscape types mixed into outbound audio at ~ -30dB to -32dB.
 */
public enum AmbientSound {
    OFF,
    OFFICE,
    CALL_CENTER,
    NATURAL_LINE,
    CAFE;

    @JsonCreator
    public static AmbientSound fromString(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
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
