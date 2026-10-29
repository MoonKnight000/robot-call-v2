package uz.murodjon.robotcallv2.voice.domain.enums;

import java.util.Locale;

/** How a catalog voice sounds, for the voice picker's filter. Null on a voice nobody described. */
public enum VoiceGender {
    FEMALE,
    MALE,
    NEUTRAL;

    /**
     * The vendor's own word for it — ElevenLabs says "female", Cartesia "feminine", Gemini
     * "FEMALE". Null for a missing or unknown word, so a new vendor spelling leaves the voice
     * unfiltered instead of failing the sync.
     */
    public static VoiceGender parse(String vendorValue) {
        if (vendorValue == null) {
            return null;
        }
        return switch (vendorValue.trim().toLowerCase(Locale.ROOT)) {
            case "female", "feminine", "woman", "f" -> FEMALE;
            case "male", "masculine", "man", "m" -> MALE;
            case "neutral", "gender_neutral", "androgynous", "non-binary", "nonbinary" -> NEUTRAL;
            default -> null;
        };
    }
}
