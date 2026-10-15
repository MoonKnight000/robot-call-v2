package uz.murodjon.robotcallv2.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The free trial a self-registered company starts with ({@code config/billing.yml}).
 *
 * @param creditUzs  how much it is given; 0 turns trials off
 * @param duration   how long before the unspent part is taken back
 * @param expiryCron how often ended trials are looked for
 */
@ConfigurationProperties(prefix = "voice-agent.billing.trial")
public record TrialCreditProperties(
        long creditUzs,
        Duration duration,
        String expiryCron
) {
}
