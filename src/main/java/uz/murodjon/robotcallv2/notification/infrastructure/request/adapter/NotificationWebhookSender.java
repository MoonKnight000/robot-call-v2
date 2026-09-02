package uz.murodjon.robotcallv2.notification.infrastructure.request.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Delivers one notification event as a plain HTTP POST (§11 settings).
 */
@Component
public class NotificationWebhookSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebhookSender.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public void send(String url, NotificationType type, String title, String message, String link) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("type", type.name());
            body.put("title", title);
            body.put("message", message);
            body.put("link", link);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Notification webhook {} HTTP {}", url, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Notification webhook {} failed: {}", url, e.getMessage());
        }
    }
}
