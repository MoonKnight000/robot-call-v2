package uz.murodjon.robotcallv2.chat.application.dto;

import java.time.Instant;

/**
 * A bot as the panel lists it. Neither the token nor the webhook secret is here: one is the
 * bot's identity and the other is what proves an update is Telegram's.
 *
 * @param webhookUrl the address Telegram was told to send updates to, once it has been
 *                   registered from this panel; null when it has not
 */
public record TelegramBotRow(
        long id,
        long agentId,
        String agentName,
        String name,
        String tokenSecretKey,
        String language,
        boolean enabled,
        String webhookUrl,
        Instant createdAt
) {
}
