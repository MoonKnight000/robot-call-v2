package uz.murodjon.robotcallv2.webhook.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClientFactory;
import uz.murodjon.robotcallv2.shared.util.WebhookSignature;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookSenderPort;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The one place an outbound webhook actually leaves the process.
 *
 * <p>The timeouts are the point of centralising it. A webhook goes to an address the
 * customer typed, and a post-call hook runs on a pool thread where an unbounded read
 * would hold that thread for as long as the far end feels like holding it. Five seconds
 * is longer than any endpoint that is working needs, and short enough that one that is
 * not cannot accumulate.
 *
 * <p>The client comes from {@link PublicHttpClientFactory}: the host is resolved only to
 * public addresses at connect time, so a name that passed the caller's screening cannot
 * be re-pointed inside the network between the check and the POST, and redirects are not
 * followed.
 *
 * <p>The signature is the second reason. A receiver has no other way to tell our POST
 * from anyone else's: the URL is often guessable and the payload says what it likes.
 */
@Component
public class RestClientWebhookSenderAdapter implements WebhookSenderPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    public RestClientWebhookSenderAdapter() {
        this.restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(
                        PublicHttpClientFactory.create(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
    }

    @Override
    public void post(String url, Map<String, String> headers, Map<String, Object> payload, String signingSecret,
                     String deliveryId) {
        request(url, headers, payload, signingSecret, deliveryId).retrieve().toBodilessEntity();
    }

    @Override
    public Map<String, Object> postForObject(String url, Map<String, String> headers, Map<String, Object> payload,
                                             String signingSecret, String deliveryId) {
        return request(url, headers, payload, signingSecret, deliveryId).retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private RestClient.RequestBodySpec request(String url, Map<String, String> headers, Map<String, Object> payload,
                                               String signingSecret, String deliveryId) {
        // Serialised here rather than handed to RestClient as an object, because the
        // signature has to cover the exact bytes that go on the wire.
        String body = serialize(payload);

        RestClient.RequestBodySpec spec = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(spec::header);
        }
        if (deliveryId != null) {
            spec.header(WebhookSignature.DELIVERY_HEADER, deliveryId);
        }
        if (signingSecret != null && !signingSecret.isBlank()) {
            spec.header(WebhookSignature.SIGNATURE_HEADER,
                    WebhookSignature.createHeaderValue(body, signingSecret, Instant.now().getEpochSecond()));
        }
        return spec.body(body);
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new ExternalServiceException(ErrorCode.WEBHOOK_PAYLOAD_UNSERIALIZABLE, "webhook", e);
        }
    }
}
