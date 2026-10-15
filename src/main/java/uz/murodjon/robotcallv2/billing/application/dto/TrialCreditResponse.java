package uz.murodjon.robotcallv2.billing.application.dto;

import java.time.Instant;

/**
 * The company's free trial, for the billing screen's "N kun qoldi" banner.
 *
 * @param active      true until the trial ends and its remainder is taken back
 * @param clawbackUzs how much was taken back when it ended; null while active
 */
public record TrialCreditResponse(
        long creditUzs,
        Instant grantedAt,
        Instant expiresAt,
        boolean active,
        Long clawbackUzs
) {
}
