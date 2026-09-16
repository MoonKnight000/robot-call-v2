package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The {@code error} member of a Payme answer.
 *
 * @param code    Payme's error code, e.g. -31050
 * @param message what Payme shows the payer, in three languages
 * @param data    the account field an account error is about; left out when there is none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymeErrorBody(
        int code,
        PaymeErrorMessage message,
        String data
) {
}
