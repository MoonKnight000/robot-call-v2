package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The answer of CreateTransaction ({@code create_time}), PerformTransaction
 * ({@code perform_time}) and CancelTransaction ({@code cancel_time}); each sets only its own
 * time, the others are left out.
 *
 * @param transaction our id for the transaction
 * @param state       Payme's number for it: 1, 2 or -1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymeTransactionResult(
        @JsonProperty("create_time") Long createTime,
        @JsonProperty("perform_time") Long performTime,
        @JsonProperty("cancel_time") Long cancelTime,
        String transaction,
        int state
) {
}
