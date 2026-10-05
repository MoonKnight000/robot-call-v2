package uz.murodjon.robotcallv2.notification.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSenderPort;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.config.NotificationProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Delivers one notification event via the Telegram Bot API's sendMessage (§11 settings).
 */
@Component
public class TelegramNotificationSenderAdapter implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationSenderAdapter.class);

    private final NotificationProperties notificationProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TelegramNotificationSenderAdapter(NotificationProperties notificationProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public NotificationChannelType resolveChannelType() {
        return NotificationChannelType.TELEGRAM;
    }

    @Override
    public void send(String chatId, NotificationType type, String title, String message, String link,
                     String signingSecret) {
        if (notificationProperties.telegramBotToken() == null || notificationProperties.telegramBotToken().isBlank()) {
            log.warn("Telegram notification to {} skipped: voice-agent.notification.telegram-bot-token not set", chatId);
            return;
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("chat_id", chatId);
            body.put("text", title + (message != null ? "\n" + message : ""));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + notificationProperties.telegramBotToken() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Telegram notification to {} HTTP {}", chatId, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Telegram notification to {} failed: {}", chatId, e.getMessage());
        }
    }
}
