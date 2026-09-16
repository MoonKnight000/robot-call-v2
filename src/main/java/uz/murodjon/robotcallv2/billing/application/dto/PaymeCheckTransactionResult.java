package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CheckTransaction's answer: every field always present, 0 for a time that has not come,
 * and {@code reason} null unless the transaction was cancelled.
 */
public record PaymeCheckTransactionResult(
        @JsonProperty("create_time") long createTime,
        @JsonProperty("perform_time") long performTime,
        @JsonProperty("cancel_time") long cancelTime,
        String transaction,
        int state,
        Integer reason
) {
}
