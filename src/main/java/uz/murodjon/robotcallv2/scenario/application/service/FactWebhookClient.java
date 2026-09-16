package uz.murodjon.robotcallv2.scenario.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.application.port.input.FactWebhookUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.output.FactWebhookFetcherPort;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhook;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;
import java.time.Duration;

/**
 * Asks a company's own system for this call's facts, before the line carries any speech
 * ({@link FactWebhook}) — outbound, before the number is dialled; inbound, while the caller
 * is still hearing ringback.
 *
 * <p>Fails open, always. A call placed with facts a few days old is a worse call; a
 * campaign that stops dialling because somebody's API is down is an outage. Every failure
 * here — timeout, non-2xx, unreachable host, a body that is not JSON — is logged and the
 * call goes on with the facts it already had.
 */
@Component
public class FactWebhookClient implements FactWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(FactWebhookClient.class);

    /** Kept short on purpose: this wait sits between claiming a target and dialling it. */
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final int MAX_TIMEOUT_MS = 2000;

    private final FactWebhookFetcherPort factWebhookFetcher;

    public FactWebhookClient(FactWebhookFetcherPort factWebhookFetcher) {
        this.factWebhookFetcher = factWebhookFetcher;
    }

    /**
     * @return the endpoint's JSON body, or null when it could not be used for any reason.
     *         Callers read only their scenario's declared facts out of it
     *         ({@code CallContextFactory#overlayWebhookFacts}).
     */
    public String fetchFacts(FactWebhook webhook, FactWebhookRequest call) {
        if (webhook == null || webhook.url() == null || webhook.url().isBlank() || call == null) {
            return null;
        }

        URI uri = PublicUrlGuard.parsePublic(webhook.url());
        if (uri == null) {
            return null;
        }

        Duration timeout = Duration.ofMillis(webhook.timeoutMs() > 0
                ? Math.min(webhook.timeoutMs(), MAX_TIMEOUT_MS)
                : DEFAULT_TIMEOUT_MS);

        try {
            String json = factWebhookFetcher.fetch(webhook, uri, call, timeout);
            return json == null || json.isBlank() ? null : json;
        } catch (Exception e) {
            log.warn("Fact webhook {} failed for {} {}: {}",
                    uri.getHost(), call.direction(), call.phone(), e.getMessage());
            return null;
        }
    }
}
