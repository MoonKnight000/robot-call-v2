package uz.murodjon.uysotvoice.shared.api;

import java.util.List;

/**
 * The one shape every API in this project returns (project API standard): the payload,
 * a human-readable message, whether the request succeeded, and any error details.
 * Controllers return this directly, or wrap it in {@code ResponseEntity} when a
 * non-200 status is needed.
 */
public record ResponseData<T>(T data, String message, boolean accept, List<String> errors) {

    public static <T> ResponseData<T> ok(T data) {
        return new ResponseData<>(data, null, true, null);
    }

    public static <T> ResponseData<T> ok(T data, String message) {
        return new ResponseData<>(data, message, true, null);
    }

    public static <T> ResponseData<T> error(String message) {
        return new ResponseData<>(null, message, false, null);
    }

    public static <T> ResponseData<T> error(String message, List<String> errors) {
        return new ResponseData<>(null, message, false, errors);
    }
}
