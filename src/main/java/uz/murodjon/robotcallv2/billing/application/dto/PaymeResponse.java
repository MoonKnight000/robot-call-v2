package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymeError;

/**
 * Payme's JSON-RPC answer: a {@code result} or an {@code error}, never both, and the request
 * {@code id} (null when the request could not be read). Not a {@code ResponseData} — Payme
 * reads only its own shape, and the HTTP status is 200 either way.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymeResponse(
        Object result,
        PaymeErrorBody error,
        @JsonInclude(JsonInclude.Include.ALWAYS) Object id
) {
    public static PaymeResponse ofResult(Object id, Object result) {
        return new PaymeResponse(result, null, id);
    }

    /** @param data the account field an account error is about, the method name for -32601; null otherwise */
    public static PaymeResponse ofError(Object id, PaymeError error, String data) {
        PaymeErrorMessage message = new PaymeErrorMessage(error.ru(), error.uz(), error.en());
        return new PaymeResponse(null, new PaymeErrorBody(error.code(), message, data), id);
    }
}
