package uz.murodjon.uysotvoice.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The requested entity does not exist — or exists but belongs to another company, which
 * is reported the same way on purpose: telling a caller "that campaign exists, just not
 * yours" leaks which ids are taken.
 */
public class NotFoundException extends AppException {

    public NotFoundException(ErrorCode code, Object... args) {
        super(HttpStatus.NOT_FOUND, code, code.format(args));
    }
}
