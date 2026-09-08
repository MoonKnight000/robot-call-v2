package uz.murodjon.robotcallv2.webhook.domain.service;

import java.util.Map;

/**
 * The secret a company's outbound webhooks are signed with.
 *
 * <p>One secret per company rather than one per hook: a receiver verifies deliveries from
 * us, not from a particular agent, and asking a customer to manage a key per webhook is
 * how signing ends up switched off. A company that has not created the secret gets
 * unsigned deliveries, which is what every company got before signing existed.
 */
public final class WebhookSigningSecret {

    /** The name the company gives the secret in the secret store. */
    public static final String SECRET_KEY = "WEBHOOK_SIGNING_SECRET";

    private WebhookSigningSecret() {
    }

    /**
     * @return the signing secret, or {@code null} when this company has not set one —
     *         the sender reads null as "send it unsigned"
     */
    public static String findIn(Map<String, String> companySecrets) {
        if (companySecrets == null) {
            return null;
        }
        String secret = companySecrets.get(SECRET_KEY);
        return secret == null || secret.isBlank() ? null : secret;
    }
}
