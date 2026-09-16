package uz.murodjon.robotcallv2.chat.domain.entity;

/**
 * The two things this platform reads out of a Telegram update: who wrote, and what.
 *
 * <p>Telegram's payload carries dozens of fields and grows with every release. Naming the
 * two that matter — rather than mapping the document — is what keeps an update shape nobody
 * here has heard of from being an error instead of a message.
 *
 * @param chatId the conversation's id at Telegram's end, as text: it is an identifier, and
 *               a group chat's is negative and large
 */
public record TelegramUpdate(String chatId, String text) {
}
