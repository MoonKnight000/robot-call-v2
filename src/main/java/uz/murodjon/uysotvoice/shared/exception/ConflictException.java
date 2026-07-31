package uz.murodjon.uysotvoice.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The request was well-formed but the system is not in a state to serve it: no ARI
 * connection, the channel already hung up, TTS disabled, a phone number already on the
 * list. Unlike a {@link ValidationException} the same request may well succeed later.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, "CONFLICT", message, cause);
    }
}
