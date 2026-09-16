package uz.murodjon.robotcallv2.chat.infrastructure.adapter;

import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.murodjon.robotcallv2.chat.application.port.output.TelegramApiPort;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClientFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Telegram's Bot API over HTTPS (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * <p>The client comes from {@link PublicHttpClientFactory} like every other outbound call
 * here: the host is resolved only to public addresses, so a bot token cannot be posted to
 * something inside the network by a DNS answer nobody checked.
 *
 * <p>A Telegram message is capped at 4096 characters and a longer one is rejected outright,
 * so a long answer is cut rather than lost — the person reads most of it instead of nothing.
 */
@Component
public class RestClientTelegramApiAdapter implements TelegramApiPort {

    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_MESSAGE_CHARS = 4096;

    private final RestClient restClient;

    public RestClientTelegramApiAdapter() {
        this.restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(
                        PublicHttpClientFactory.create(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
    }

    @Override
    public void sendMessage(String botToken, String chatId, String text) {
        String message = text == null ? "" : text;
        call(botToken, "sendMessage", Map.of(
                "chat_id", chatId,
                "text", message.length() > MAX_MESSAGE_CHARS ? message.substring(0, MAX_MESSAGE_CHARS) : message));
    }

    @Override
    public void registerWebhook(String botToken, String url, String secret) {
        call(botToken, "setWebhook", Map.of(
                "url", url,
                "secret_token", secret,
                // Only what this platform reads. Telegram sends far more by default, and every
                // kind of update it sends is one more thing arriving at an open endpoint.
                "allowed_updates", new String[]{"message"},
                "drop_pending_updates", true));
    }

    @Override
    public void removeWebhook(String botToken) {
        call(botToken, "deleteWebhook", Map.of("drop_pending_updates", true));
    }

    private void call(String botToken, String method, Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri(API_BASE + botToken + "/" + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            // The token is in the address, so the vendor's own message is the only thing that
            // may be repeated — never the URL this was called with.
            throw new ExternalServiceException(ErrorCode.TELEGRAM_API_FAILED, "telegram", e, method);
        }
    }
}
