package uz.murodjon.robotcallv2.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One transaction in a GetStatement answer.
 *
 * @param id          Payme's transaction id
 * @param time        when Payme created it, epoch milliseconds
 * @param amount      in tiyin
 * @param transaction our id for it
 * @param receivers   always null: a top-up has no split receivers
 */
public record PaymeStatementTransaction(
        String id,
        long time,
        long amount,
        PaymeAccount account,
        @JsonProperty("create_time") long createTime,
        @JsonProperty("perform_time") long performTime,
        @JsonProperty("cancel_time") long cancelTime,
        String transaction,
        int state,
        Integer reason,
        List<Object> receivers
) {
}
