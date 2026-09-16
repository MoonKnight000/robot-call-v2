package uz.murodjon.robotcallv2.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingRates;

/**
 * How a call gets billed ({@code config/billing.yml}).
 *
 * <p>Everything is in UZS. The providers publish USD, but converting at settlement time
 * would make a ledger row impossible to reproduce later — the rate moved — so an operator
 * writes the converted numbers here and bumps {@link #version} when they do. That version
 * is stamped on every {@code call_billing} row, which is what lets an old call still be
 * explained with the prices it was actually charged at.
 *
 * @param version        stamped on every settled call; bump it whenever a rate changes,
 *                       or changing one silently reprices the whole history
 * @param enforceBalance whether a company that cannot pay is stopped from dialling. Off by
 *                       default so that shipping this does not stop an existing deployment
 *                       dialling; turn it on once the {@code company_billing} balances are
 *                       real numbers
 * @param reservationUzs held per call before the number is dialled, released or settled
 *                       when it ends. Has to cover a call that runs the full expected
 *                       length — otherwise a company at zero can still talk for ten minutes
 * @param rates          the price list itself
 * @param autoRecharge   when an automatic top-up is raised
 */
@ConfigurationProperties(prefix = "voice-agent.billing")
public record BillingRateProperties(
        String version,
        boolean enforceBalance,
        long reservationUzs,
        BillingRates rates,
        BillingAutoRecharge autoRecharge
) {
}
