package uz.murodjon.robotcallv2.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The request was well-formed but the system is not in a state to serve it: no ARI
 * connection, the channel already hung up, TTS disabled, a phone number already on the
 * list. Unlike a {@link ValidationException} the same request may well succeed later.
 */
public class ConflictException extends AppException {

    public ConflictException(ErrorCode code, Object... args) {
        super(HttpStatus.CONFLICT, code, code.format(args));
    }
}
