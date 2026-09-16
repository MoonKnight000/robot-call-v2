package uz.murodjon.robotcallv2.voicemail.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/** Shape rules for a voicemail box. */
public final class VoicemailBoxValidator {

    public static final int MIN_SECONDS = 10;
    public static final int MAX_SECONDS = 600;
    private static final int MAX_GREETING_CHARS = 1000;

    private VoicemailBoxValidator() {
    }

    public static void validate(String name, String greetingText, int maxSeconds) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(ErrorCode.VOICEMAIL_BOX_NAME_REQUIRED);
        }
        if (greetingText != null && greetingText.length() > MAX_GREETING_CHARS) {
            throw new ValidationException(ErrorCode.VOICEMAIL_BOX_GREETING_TOO_LONG, MAX_GREETING_CHARS);
        }
        if (maxSeconds < MIN_SECONDS || maxSeconds > MAX_SECONDS) {
            throw new ValidationException(ErrorCode.VOICEMAIL_BOX_MAX_SECONDS_OUT_OF_RANGE, MIN_SECONDS, MAX_SECONDS);
        }
    }
}
