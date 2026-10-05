package uz.murodjon.robotcallv2.agent.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.shared.util.WebhookSignature;
import uz.murodjon.robotcallv2.webhook.domain.service.WebhookSigningSecret;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asks a company's own endpoint which way a WEBHOOK_ROUTER node goes — "is this caller a
 * debtor", "which branch owns this number". The caller is on an open line meanwhile, so
 * the budget is short and every failure means "take the error exit", never a stuck call.
 *
 * <p>Signed like the company's other webhooks when it has set {@code WEBHOOK_SIGNING_SECRET},
 * so the endpoint can tell this question from a stranger's.
 */
@Component
public class FlowWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(FlowWebhookClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** An answer is {@code {"exit": "<name>"}}; anything this size is not one. */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final ObjectMapper objectMapper;

    private final PublicHttpClient httpClient = PublicHttpClient.create(TIMEOUT, TIMEOUT);
    private final SecretUseCase secretUseCase;

    public FlowWebhookClient(SecretUseCase secretUseCase, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.secretUseCase = secretUseCase;
    }

    /**
     * POSTs the caller's details and reads {@code {"exit": "<name>"}} back.
     *
     * @return the exit name, or null on any failure
     */
    public String askExit(String url, FlowCall call) {
        URI uri = PublicUrlGuard.parsePublic(url);
        if (uri == null) {
            log.warn("[{}] flow webhook refused — not a public http(s) address", call.channelId());
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("callerNumber", call.callerNumber());
            body.put("did", call.did());
            body.put("language", call.language());
            body.put("callId", call.callAttemptId());
            body.put("vip", call.vip());
            body.put("choices", call.choices());
            String json = objectMapper.writeValueAsString(body);

            PublicHttpRequest request = PublicHttpRequest.create("POST", uri)
                    .setTimeout(TIMEOUT)
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(WebhookSignature.DELIVERY_HEADER, UUID.randomUUID().toString())
                    .setBody(json);
            String secret = WebhookSigningSecret.findIn(secretUseCase.findDecryptedSecrets(call.companyId()));
            if (secret != null) {
                request.addHeader(WebhookSignature.SIGNATURE_HEADER,
                        WebhookSignature.createHeaderValue(json, secret, Instant.now().getEpochSecond()));
            }

            PublicHttpResponse response = httpClient.send(request);
            if (response.statusCode() / 100 != 2) {
                log.warn("[{}] flow webhook {} answered HTTP {}", call.channelId(), uri.getHost(), response.statusCode());
                return null;
            }
            JsonNode exit = objectMapper.readTree(response.readBodyText()).get("exit");
            return exit != null && exit.isTextual() ? exit.asText() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("[{}] flow webhook to {} failed: {}", call.channelId(), uri.getHost(), e.getMessage());
            return null;
        }
    }
}
