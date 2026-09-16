package uz.murodjon.robotcallv2.voice.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * The voice-speed bounds — the one definition the company voice settings and every agent
 * ({@code AiAgentValidator}) are checked against. 0.25–3.0 is what every synthesizer a
 * per-call speed reaches accepts: Yandex takes at most 3.0, Google and OpenAI at least
 * 0.25. A wider range would save a value some vendor rejects or quietly clamps, and past
 * about twice the normal pace speech on a phone line stops being understood anyway.
 */
public final class VoiceSettingsValidator {

    private static final double MIN_SPEED = 0.25;
    private static final double MAX_SPEED = 3.0;

    private VoiceSettingsValidator() {
    }

    public static void validateSpeed(Double speed) {
        // null is allowed (default)
        if (speed != null && (speed < MIN_SPEED || speed > MAX_SPEED)) {
            throw new ValidationException(ErrorCode.VOICE_SPEED_OUT_OF_RANGE, speed);
        }
    }
}
