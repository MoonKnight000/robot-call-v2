package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Click's answer to Prepare ({@code merchant_prepare_id}) or Complete
 * ({@code merchant_confirm_id}). Not a {@code ResponseData} — Click reads only its own
 * shape, and the HTTP status is 200 whatever {@code error} says.
 *
 * @param error     0 for success, one of Click's negative codes otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClickResponse(
        @JsonProperty("click_trans_id") Long clickTransId,
        @JsonProperty("merchant_trans_id") String merchantTransId,
        @JsonProperty("merchant_prepare_id") Long merchantPrepareId,
        @JsonProperty("merchant_confirm_id") Long merchantConfirmId,
        int error,
        @JsonProperty("error_note") String errorNote
) {
}
