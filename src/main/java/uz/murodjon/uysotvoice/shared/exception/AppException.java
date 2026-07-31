package uz.murodjon.uysotvoice.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base of every exception this application throws on purpose.
 *
 * <p>The point is that a service never has to know it is being called over HTTP. It says
 * <em>what</em> went wrong — not found, invalid, conflicting — and the status that maps to
 * travels with the exception, so {@code ApiExceptionHandler} can turn any of them into a
 * response without a chain of {@code instanceof} checks and without services importing
 * {@code ResponseStatusException}.
 *
 * <p>Anything that is <em>not</em> an {@code AppException} is a bug: the handler logs it in
 * full and reports a generic 500, because an unexpected exception's internals are never a
 * client's business.
 */
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected AppException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /** HTTP status this failure maps to. */
    public HttpStatus status() {
        return status;
    }

    /**
     * Stable machine-readable code (e.g. {@code NOT_FOUND}, {@code CONFLICT}). Clients
     * branch on this rather than on the message, which is free to be reworded.
     */
    public String code() {
        return code;
    }
}
