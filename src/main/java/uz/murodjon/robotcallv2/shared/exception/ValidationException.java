package uz.murodjon.robotcallv2.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The caller sent something the system cannot accept — a malformed phone number, a blank
 * text to speak, a playback path outside the recording directory. Retrying the same
 * request will fail the same way; the request itself has to change.
 *
 * <p>A domain validator that finds several problems at once (a scenario definition, a call
 * flow graph) raises one exception through {@link #withErrors} — its {@code code} says
 * what was being validated, and each entry of {@code errors} names one problem. They reach
 * the client in {@code ResponseData.errors}, the same field {@code @Valid} failures fill.
 */
public class ValidationException extends AppException {

    private final List<String> errors;

    public ValidationException(ErrorCode code, Object... args) {
        super(HttpStatus.BAD_REQUEST, code, code.format(args));
        this.errors = null;
    }

    private ValidationException(ErrorCode code, List<String> errors, Object... args) {
        super(HttpStatus.BAD_REQUEST, code, code.format(args));
        this.errors = List.copyOf(errors);
    }

    /**
     * A failure with one detail per problem found — each entry is sent as-is in
     * {@code ResponseData.errors}, in the {@code "field: message"} shape of {@code @Valid}
     * failures.
     */
    public static ValidationException withErrors(ErrorCode code, List<String> errors, Object... args) {
        return new ValidationException(code, errors, args);
    }

    @Override
    public List<String> errors() {
        return errors;
    }
}
