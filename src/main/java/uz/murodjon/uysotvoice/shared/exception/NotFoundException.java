package uz.murodjon.uysotvoice.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The requested entity does not exist — or exists but belongs to another company, which
 * is reported the same way on purpose: telling a caller "that campaign exists, just not
 * yours" leaks which ids are taken.
 */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /** @param entity lower-case entity name, e.g. {@code campaign} */
    public NotFoundException(String entity, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " " + id + " not found");
    }
}
