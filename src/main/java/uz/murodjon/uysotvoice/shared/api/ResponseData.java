package uz.murodjon.uysotvoice.shared.api;

import uz.murodjon.uysotvoice.shared.exception.ErrorCode;

import java.util.List;

/**
 * The one shape every API in this project returns (project API standard): the payload,
 * a human-readable message, the code a frontend translates that message by, whether the
 * request succeeded, and any error details. Controllers return this directly, or wrap it
 * in {@code ResponseEntity} when a non-200 status is needed.
 */
public record ResponseData<T>(T data, String message, String messageCode, boolean accept, List<String> errors) {

    public static <T> ResponseData<T> ok(T data) {
        return new ResponseData<>(data, null, null, true, null);
    }

    public static <T> ResponseData<T> error(ErrorCode code, String message) {
        return new ResponseData<>(null, message, code.name(), false, null);
    }

    public static <T> ResponseData<T> error(ErrorCode code, String message, List<String> errors) {
        return new ResponseData<>(null, message, code.name(), false, errors);
    }
}
