package uz.murodjon.uysotvoice.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * A dependency the request needed — Asterisk, the CRM, an STT/TTS provider, object
 * storage — failed or refused. Reported as 502 rather than 500 so an operator reading the
 * response can tell "our bug" from "their outage" without opening the logs.
 */
public class ExternalServiceException extends AppException {

    /** @param service the dependency that failed, e.g. {@code asterisk}, {@code crm} */
    public ExternalServiceException(ErrorCode code, String service, Object... args) {
        super(HttpStatus.BAD_GATEWAY, code, service + ": " + code.format(args));
    }

    public ExternalServiceException(ErrorCode code, String service, Throwable cause, Object... args) {
        super(HttpStatus.BAD_GATEWAY, code, service + ": " + code.format(args), cause);
    }
}
