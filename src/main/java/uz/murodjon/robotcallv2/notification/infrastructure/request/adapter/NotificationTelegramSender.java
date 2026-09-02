package uz.murodjon.robotcallv2.notification.infrastructure.request.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
public class NotificationTelegramSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationTelegramSender.class);

    private final NotificationProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public NotificationTelegramSender(NotificationProperties props) {
        this.props = props;
    }

    public void send(String chatId, String text) {
        if (props.telegramBotToken() == null || props.telegramBotToken().isBlank()) {
            log.warn("Telegram notification to {} skipped: voice-agent.notification.telegram-bot-token not set", chatId);
            return;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("chat_id", chatId);
            body.put("text", text);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + props.telegramBotToken() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Telegram notification to {} HTTP {}", chatId, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Telegram notification to {} failed: {}", chatId, e.getMessage());
        }
    }
}
