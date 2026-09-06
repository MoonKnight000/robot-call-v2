package uz.murodjon.robotcallv2.voice.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class VoiceSettingsValidator {

    private VoiceSettingsValidator() {
    }

    public static void validateSpeed(Double speed) {
        // null is allowed (default)
        if (speed != null && (speed < 0.25 || speed > 4.0)) {
            throw new ValidationException(ErrorCode.VOICE_SPEED_OUT_OF_RANGE, speed);
        }
    }
}
