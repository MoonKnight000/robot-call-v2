package uz.murodjon.uysotvoice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.shared.exception.AppException;

import java.util.List;

/**
 * The one place an exception becomes an HTTP response, always in the project's standard
 * {@link ResponseData} envelope. Without this a rejected phone number, a malformed request
 * body, or a hung-up channel would each surface differently — some as a bare 500, some as
 * Spring Boot's default {@code /error} shape — which tells a client nothing about whether
 * retrying could help and breaks the one-envelope API standard.
 *
 * <p>The design is deliberately two-tier:
 *
 * <ul>
 *   <li>{@link AppException} → the status it carries. Services describe <em>what</em> went
 *       wrong ({@code NotFoundException}, {@code ValidationException}, {@code
 *       ConflictException}, …) and never import anything from {@code org.springframework.http},
 *       so the HTTP mapping lives here and nowhere else.</li>
 *   <li>The framework's own request-level failures — a body that is not valid JSON, a
 *       missing parameter, a path variable of the wrong type — which Spring throws before
 *       any of our code runs, so they cannot be expressed as an {@code AppException}.</li>
 * </ul>
 *
 * <p>Anything else is a bug: logged in full server-side, reported to the caller as a
 * generic 500. An unexpected exception's internals are never a client's business.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Every failure the application raises on purpose. */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ResponseData<Object>> appException(AppException e) {
        return respond(e.status(), e.getMessage(), null, e);
    }

    /** A {@code @Valid @RequestBody} failed Bean Validation — one message per failed field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseData<Object>> validationFailed(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", errors, e);
    }

    /**
     * The request body is not valid JSON, or a field holds a value the target type cannot
     * accept (e.g. an unknown sort-column name in a filter's {@code orders} map).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseData<Object>> malformedBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed or invalid request body", null, e);
    }

    /** A required {@code @RequestParam} was left out entirely. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseData<Object>> missingParameter(MissingServletRequestParameterException e) {
        return respond(HttpStatus.BAD_REQUEST, "Missing required parameter: " + e.getParameterName(), null, e);
    }

    /** A path/query value could not be converted to the parameter's type (e.g. a non-numeric id). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseData<Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + e.getName(), null, e);
    }

    /**
     * No handler matched the request — a typo'd path, or an {@code Accept} header that
     * doesn't satisfy a mapping's {@code produces} (e.g. hitting {@code /listen} without
     * accepting {@code audio/wav}). Spring's own resolution falls through to the static-
     * resource handler in this case, not a routing 404 — normalized here so it doesn't
     * look like a server bug.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseData<Object>> noResourceFound(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "No such endpoint: " + e.getResourcePath(), null, e);
    }

    /** Anything not handled above is a bug, not a client error. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseData<Object>> unexpected(Exception e) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null, e);
    }

    /**
     * Builds the one error shape every handler above returns. Logging level follows the
     * status: a 5xx is always worth the full stack trace, a 4xx from ordinary bad input is
     * not — it would just drown the logs the one time it is actually a server bug.
     */
    private ResponseEntity<ResponseData<Object>> respond(HttpStatus status, String message,
                                                         List<String> errors, Exception cause) {
        if (status.is5xxServerError()) {
            log.error("Request failed with {}", status, cause);
        } else {
            log.debug("Rejected request with {}: {}", status, message);
        }
        return ResponseEntity.status(status).body(ResponseData.error(message, errors));
    }
}
