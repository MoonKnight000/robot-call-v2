package uz.murodjon.robotcallv2.voice.domain.service;

public final class VoiceSettingsValidator {

    private VoiceSettingsValidator() {
    }

    public static void validateSpeed(Double speed) {
        // null is allowed (default)
        if (speed != null && (speed < 0.25 || speed > 4.0)) {
            throw new IllegalArgumentException("Speed must be between 0.25 and 4.0");
        }
    }
}
