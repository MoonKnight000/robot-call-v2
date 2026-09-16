package uz.murodjon.robotcallv2.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller has done this too often in too short a time — a login guessed once too many,
 * a public endpoint hammered from one address. Nothing about the request itself is wrong;
 * the same request succeeds once the window has passed (429).
 */
public class TooManyRequestsException extends AppException {

    public TooManyRequestsException(ErrorCode code, Object... args) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, code.format(args));
    }
}
