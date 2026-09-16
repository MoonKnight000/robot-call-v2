package uz.murodjon.robotcallv2.chat.domain.entity;

import java.time.Instant;

/**
 * A company's Telegram bot, answering with one of its agents
 * (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * @param tokenSecretKey the company secret holding the bot token. Named, never held: the
 *                       token is the bot, and a bot token in a table read by half the
 *                       application is a bot somebody else can be
 * @param webhookSecret  ours, sent to Telegram with the webhook address and returned on every
 *                       update. The address alone is a guessable URL; this is what makes an
 *                       update provably Telegram's
 * @param language      what the bot answers in; null uses the agent's own
 * @param webhookUrl    where Telegram was told to send updates; null until the panel registers it
 */
public record TelegramBot(
        Long id,
        long companyId,
        long aiAgentId,
        String name,
        String tokenSecretKey,
        String webhookSecret,
        String language,
        String webhookUrl,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    /** The same bot, now pointed at an address Telegram will send its updates to. */
    public TelegramBot withWebhookUrl(String url) {
        return new TelegramBot(id, companyId, aiAgentId, name, tokenSecretKey, webhookSecret, language, url,
                enabled, createdAt, updatedAt);
    }
}
