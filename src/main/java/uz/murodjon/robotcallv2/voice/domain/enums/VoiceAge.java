package uz.murodjon.robotcallv2.voice.domain.enums;

import java.util.Locale;

/** How old a catalog voice sounds, for the voice picker's filter. Null on a voice nobody described. */
public enum VoiceAge {
    YOUNG,
    MIDDLE_AGED,
    OLD;

    /** The vendor's own word for it (ElevenLabs: "young", "middle_aged", "old"); null when unknown. */
    public static VoiceAge parse(String vendorValue) {
        if (vendorValue == null) {
            return null;
        }
        return switch (vendorValue.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_')) {
            case "young" -> YOUNG;
            case "middle_aged", "middle" -> MIDDLE_AGED;
            case "old" -> OLD;
            default -> null;
        };
    }
}
