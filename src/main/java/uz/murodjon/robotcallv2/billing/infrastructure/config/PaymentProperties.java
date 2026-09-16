package uz.murodjon.robotcallv2.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How top-ups are paid ({@code config/billing.yml}, {@code voice-agent.billing.payment.*}).
 *
 * <p>No credentials ship with the code. A provider left unconfigured refuses its top-ups
 * with 409 {@code PAYMENT_METHOD_NOT_CONFIGURED} and its callbacks as unauthenticated —
 * never a link to a made-up merchant.
 *
 * @param topupTtl   how long a top-up may stay unpaid before it is marked EXPIRED
 * @param expiryCron how often the expiry sweep runs
 * @param payme      Payme merchant settings
 * @param click      Click merchant settings
 */
@ConfigurationProperties(prefix = "voice-agent.billing.payment")
public record PaymentProperties(
        Duration topupTtl,
        String expiryCron,
        PaymentPaymeProperties payme,
        PaymentClickProperties click
) {
    public PaymentProperties {
        if (topupTtl == null || topupTtl.isZero() || topupTtl.isNegative()) {
            topupTtl = Duration.ofHours(24);
        }
        if (payme == null) {
            payme = new PaymentPaymeProperties(false, null, null, null, null, null, null, 0);
        }
        if (click == null) {
            click = new PaymentClickProperties(false, null, null, null, null);
        }
    }
}
