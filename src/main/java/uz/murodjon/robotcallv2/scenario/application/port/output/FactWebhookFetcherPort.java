package uz.murodjon.robotcallv2.scenario.application.port.output;

import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhook;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;

import java.net.URI;
import java.time.Duration;

/** One request to a company's own fact endpoint ({@link FactWebhook}). */
public interface FactWebhookFetcherPort {

    /**
     * @param uri     {@code webhook.url()} after PublicUrlGuard has screened it
     * @param timeout the deadline for the whole request
     * @return the body of a 2xx answer, possibly blank. Raises ExternalServiceException when
     *         the endpoint cannot be reached, answers other than 2xx, or answers too much
     */
    String fetch(FactWebhook webhook, URI uri, FactWebhookRequest call, Duration timeout);
}
