package uz.murodjon.uysotvoice.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated but not allowed to do this — e.g. editing a built-in
 * scenario that every company shares.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(ErrorCode code, Object... args) {
        super(HttpStatus.FORBIDDEN, code, code.format(args));
    }
}
