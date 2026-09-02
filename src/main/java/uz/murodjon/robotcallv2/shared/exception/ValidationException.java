package uz.murodjon.robotcallv2.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller sent something the system cannot accept — a malformed phone number, a blank
 * text to speak, a playback path outside the recording directory. Retrying the same
 * request will fail the same way; the request itself has to change.
 */
public class ValidationException extends AppException {

    public ValidationException(ErrorCode code, Object... args) {
        super(HttpStatus.BAD_REQUEST, code, code.format(args));
    }
}
