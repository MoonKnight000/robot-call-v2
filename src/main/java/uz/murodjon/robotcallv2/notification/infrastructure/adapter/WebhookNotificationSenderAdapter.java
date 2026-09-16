package uz.murodjon.robotcallv2.notification.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSenderPort;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.shared.util.WebhookSignature;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Delivers one notification event as a plain HTTP POST (§11 settings), signed like the
 * company's other webhooks when it has a signing secret.
 */
@Component
public class WebhookNotificationSenderAdapter implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationSenderAdapter.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** The answer is not read; this much is taken off the wire before the connection is dropped. */
    private static final int MAX_RESPONSE_BYTES = 4 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicHttpClient publicHttpClient = PublicHttpClient.create(TIMEOUT, TIMEOUT);

    @Override
    public NotificationChannelType resolveChannelType() {
        return NotificationChannelType.WEBHOOK;
    }

    @Override
    public void send(String url, NotificationType type, String title, String message, String link,
                     String signingSecret) {
        // Screened at send time too, not only on save: a name that resolved to a public
        // address then may point inside the network now. Only the host is ever logged —
        // Slack and Telegram webhook URLs carry their credential in the path.
        URI uri = PublicUrlGuard.parsePublic(url);
        if (uri == null) {
            log.warn("Notification webhook refused — not a public http(s) address");
            return;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("type", type.name());
            body.put("title", title);
            body.put("message", message);
            body.put("link", link);
            String json = mapper.writeValueAsString(body);
            PublicHttpRequest request = PublicHttpRequest.create("POST", uri)
                    .setTimeout(TIMEOUT)
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(WebhookSignature.DELIVERY_HEADER, UUID.randomUUID().toString())
                    .setBody(json);
            if (signingSecret != null && !signingSecret.isBlank()) {
                request.addHeader(WebhookSignature.SIGNATURE_HEADER,
                        WebhookSignature.createHeaderValue(json, signingSecret, Instant.now().getEpochSecond()));
            }
            PublicHttpResponse response = publicHttpClient.send(request);
            if (response.statusCode() / 100 != 2) {
                log.warn("Notification webhook to {} HTTP {}", uri.getHost(), response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Notification webhook to {} interrupted", uri.getHost());
        } catch (Exception e) {
            log.warn("Notification webhook to {} failed: {}", uri.getHost(), e.getMessage());
        }
    }
}
