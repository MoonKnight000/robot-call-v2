package uz.murodjon.robotcallv2.chat.application.port.output;

/**
 * What this platform asks of Telegram (UNIVERSAL-OPERATOR-PLAN E.4). Two calls: say
 * something, and tell Telegram where to send what people say back.
 *
 * <p>A port rather than a client in the service, for the usual reason and one more: the bot
 * token is handed in per call because it is the company's, read from its secret store at the
 * moment it is needed and never held anywhere else.
 */
public interface TelegramApiPort {

    void sendMessage(String botToken, String chatId, String text);

    /**
     * Points the bot at {@code url} and tells Telegram to send {@code secret} back in the
     * {@code X-Telegram-Bot-Api-Secret-Token} header of every update.
     */
    void registerWebhook(String botToken, String url, String secret);

    /** Stops the updates — what deleting a bot here does at Telegram's end. */
    void removeWebhook(String botToken);
}
