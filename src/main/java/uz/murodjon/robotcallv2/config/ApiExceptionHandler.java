package uz.murodjon.robotcallv2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.List;
import java.util.Locale;

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
        return respond(e.status(), e.code(), e.getMessage(), e.errors(), e);
    }

    /** A {@code @Valid @RequestBody} failed Bean Validation — one message per failed field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseData<Object>> validationFailed(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.format(),
                errors, e);
    }

    /**
     * A constraint on a controller parameter itself failed — {@code @Size} on a raw CSV
     * body, say. Without this it fell through to the catch-all below as a 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ResponseData<Object>> parameterValidationFailed(HandlerMethodValidationException e) {
        List<String> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.format(),
                errors, e);
    }

    /**
     * The request body is not valid JSON, or a field holds a value the target type cannot
     * accept (e.g. an unknown sort-column name in a filter's {@code orders} map).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseData<Object>> malformedBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST_BODY,
                ErrorCode.MALFORMED_REQUEST_BODY.format(), null, e);
    }

    /** A required {@code @RequestParam} was left out entirely. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseData<Object>> missingParameter(MissingServletRequestParameterException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_REQUIRED_PARAMETER,
                ErrorCode.MISSING_REQUIRED_PARAMETER.format(e.getParameterName()), null, e);
    }

    /** A path/query value could not be converted to the parameter's type (e.g. a non-numeric id). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseData<Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER_VALUE,
                ErrorCode.INVALID_PARAMETER_VALUE.format(e.getName()), null, e);
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
        return respond(HttpStatus.NOT_FOUND, ErrorCode.NO_SUCH_ENDPOINT,
                ErrorCode.NO_SUCH_ENDPOINT.format(e.getResourcePath()), null, e);
    }

    /** HTTP method is not supported on this endpoint (e.g. POST to a GET-only path). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseData<Object>> methodNotSupported(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.format(e.getMethod()), null, e);
    }

    /**
     * An SSE subscriber disconnected while a frame was being sent (UI closed the tab).
     *
     * <p>Normal client lifecycle, not a failure: do not log as ERROR or generate an HTTP
     * 500 response (the socket is already closed).
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncDisconnected(AsyncRequestNotUsableException e) {
        log.debug("SSE client disconnected: {}", e.getMessage());
    }

    /**
     * The client sent an {@code Accept} header that cannot accept JSON (e.g. {@code text/event-stream}
     * or a raw media type) on an error response.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<String> mediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Not Acceptable: " + e.getMessage());
    }

    /**
     * A {@code @PreAuthorize} on the controller refused the call. Without this the catch-all
     * below would report a missing permission as a 500 — the advice runs inside the dispatch,
     * before Spring Security own translation filter ever sees the exception.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseData<Object>> accessDenied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, ErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED.format(),
                null, e);
    }

    /**
     * An edit lost the race to another one saved between its read and its write, caught by
     * {@code @Version} when the transaction flushed — the same answer as the stale
     * {@code version} in a request that the services check first.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ResponseData<Object>> optimisticLockFailed(ObjectOptimisticLockingFailureException e) {
        return respond(HttpStatus.CONFLICT, ErrorCode.ENTITY_VERSION_CONFLICT,
                ErrorCode.ENTITY_VERSION_CONFLICT.format(entityNameOf(e), e.getIdentifier()), null, e);
    }

    /**
     * The row's kind as the services name it in the same message — {@code CampaignEntity}
     * becomes {@code campaign}, {@code AiAgentEntity} {@code ai_agent}. Hibernate does not
     * always say which entity a batched update lost; then it is only "record".
     */
    private static String entityNameOf(ObjectOptimisticLockingFailureException e) {
        Class<?> persistentClass = e.getPersistentClass();
        if (persistentClass == null) {
            return "record";
        }
        String simpleName = persistentClass.getSimpleName().replaceFirst("Entity$", "");
        return simpleName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** Catch-all for truly unexpected errors. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseData<Object>> unhandled(Exception e) {
        log.error("Request failed with 500 INTERNAL_SERVER_ERROR", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.format(), null, e);
    }

    private static ResponseEntity<ResponseData<Object>> respond(HttpStatus status, ErrorCode code,
                                                                 String message, List<String> errors,
                                                                 Exception e) {
        return ResponseEntity.status(status).body(ResponseData.error(code, message, errors));
    }
}
