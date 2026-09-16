package uz.murodjon.robotcallv2.chat.domain.service;

import uz.murodjon.robotcallv2.chat.domain.entity.TelegramUpdate;

import java.util.Map;

/**
 * Reads a Telegram update, or decides there is nothing in it to answer.
 *
 * <p>Deliberately forgiving. A bot receives far more than messages — somebody joined, a
 * photo, a poll answer, an edit, a callback — and none of those is an error: they are simply
 * not a question. Anything this cannot read comes back null and the update is acknowledged,
 * because an update Telegram does not get a 2xx for is an update Telegram sends again.
 *
 * <p>Spring-free, so the parsing can be tested without a bot, a webhook or a model.
 */
public final class TelegramUpdates {

    /** Telegram's own cap on a message; anything longer is not a message this platform has to hold. */
    private static final int MAX_TEXT_CHARS = 4096;

    private TelegramUpdates() {
    }

    /** The message in {@code update}, or null when it holds none. */
    public static TelegramUpdate read(Map<String, Object> update) {
        if (update == null) {
            return null;
        }
        Object message = update.get("message");
        if (!(message instanceof Map<?, ?> fields)) {
            return null;
        }
        Object text = fields.get("text");
        Object chat = fields.get("chat");
        if (!(text instanceof String written) || written.isBlank() || !(chat instanceof Map<?, ?> chatFields)) {
            return null;
        }
        Object chatId = chatFields.get("id");
        if (chatId == null) {
            return null;
        }
        String trimmed = written.length() > MAX_TEXT_CHARS ? written.substring(0, MAX_TEXT_CHARS) : written;
        return new TelegramUpdate(numberAsText(chatId), trimmed.trim());
    }

    /** Jackson reads a chat id as a Long or an Integer; neither may reach the database as "123.0". */
    private static String numberAsText(Object chatId) {
        return chatId instanceof Number number ? String.valueOf(number.longValue()) : chatId.toString();
    }
}
