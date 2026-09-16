package uz.murodjon.robotcallv2.billing.application.dto;

import java.math.BigDecimal;

/**
 * The parameters of every Merchant API method in one shape; each method reads its own.
 *
 * @param id      Payme's transaction id
 * @param time    when Payme created the transaction, epoch milliseconds
 * @param amount  in tiyin (UZS * 100)
 * @param account what the payer is paying for
 * @param reason  why Payme is cancelling (CancelTransaction)
 * @param from    GetStatement window start, epoch milliseconds
 * @param to      GetStatement window end, epoch milliseconds
 */
public record PaymeParams(
        String id,
        Long time,
        BigDecimal amount,
        PaymeAccount account,
        Integer reason,
        Long from,
        Long to
) {
}
