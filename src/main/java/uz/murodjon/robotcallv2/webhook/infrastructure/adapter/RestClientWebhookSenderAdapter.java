package uz.murodjon.robotcallv2.webhook.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookSenderPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
 * <p>The signature is the second reason. A receiver has no other way to tell our POST
 * from anyone else's: the URL is often guessable and the payload says what it likes.
 */
@Component
public class RestClientWebhookSenderAdapter implements WebhookSenderPort {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final String SIGNATURE_HEADER = "X-RobotCall-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    public RestClientWebhookSenderAdapter() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void post(String url, Map<String, String> headers, Map<String, Object> payload, String signingSecret) {
        request(url, headers, payload, signingSecret).retrieve().toBodilessEntity();
    }

    @Override
    public Map<String, Object> postForObject(String url, Map<String, String> headers, Map<String, Object> payload,
                                             String signingSecret) {
        return request(url, headers, payload, signingSecret).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    private RestClient.RequestBodySpec request(String url, Map<String, String> headers, Map<String, Object> payload,
                                               String signingSecret) {
        // Serialised here rather than handed to RestClient as an object, because the
        // signature has to cover the exact bytes that go on the wire.
        String body = serialize(payload);

        RestClient.RequestBodySpec spec = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(spec::header);
        }
        if (signingSecret != null && !signingSecret.isBlank()) {
            spec.header(SIGNATURE_HEADER, signature(body, signingSecret));
        }
        return spec.body(body);
    }

    /**
     * {@code t=<unix seconds>,v1=<hex HMAC-SHA256 of "t.body">}.
     *
     * <p>The timestamp is inside the signed string on purpose: without it a receiver can
     * verify that we sent the body but not when, and an old delivery replayed a month
     * later verifies just as well as a fresh one.
     */
    private static String signature(String body, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            // Every JVM ships HmacSHA256; reaching here means the platform is broken, not
            // that this webhook is misconfigured.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Webhook payload could not be serialised", new IOException(e));
        }
    }
}
