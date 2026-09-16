package uz.murodjon.robotcallv2.chat.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Map;

/**
 * One conversation with an agent, carried in text (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * <p>Everything a voice call keeps in memory for as long as the channel is up is a column
 * here instead: which stage the scenario is in, what its tools have recorded, and whether it
 * is over. A chat has no channel to keep anything in — the visitor may answer in a minute or
 * in an hour, and the next message may reach a different instance — so the conversation is
 * rebuilt from these rows and the transcript each time.
 *
 * <p>One record for both text channels. Which one a conversation belongs to is the pair of
 * fields that is set: a widget chat has a {@code widgetId} and a browser holding its
 * {@code sessionKey}, a Telegram conversation has a bot and the chat id Telegram knows the
 * person by. Everything after that — the turn, the tools, the guardrails — is identical,
 * which is the point of carrying both here rather than in two features.
 *
 * @param sessionKey     the handle the browser holds; the id is never exposed
 * @param telegramChatId the conversation at Telegram's end; null for a widget chat
 * @param stage          the scenario stage the next message will be answered in
 * @param outcome        what the scenario's tools have recorded so far
 * @param disposition    how it ended, once it has; null while it is still going
 */
public record ChatSession(
        Long id,
        String sessionKey,
        long companyId,
        long aiAgentId,
        Long widgetId,
        Long telegramBotId,
        String telegramChatId,
        String language,
        String stage,
        Map<String, Object> outcome,
        Disposition disposition,
        boolean ended,
        int messageCount,
        Instant createdAt,
        Instant lastMessageAt
) {

    public ChatSession {
        outcome = outcome == null ? Map.of() : Map.copyOf(outcome);
    }

    /** A widget conversation about to be written: the adapter assigns the id and the timestamps. */
    public static ChatSession startingOnWidget(String sessionKey, long companyId, long aiAgentId, Long widgetId,
                                               String language, String stage) {
        return new ChatSession(null, sessionKey, companyId, aiAgentId, widgetId, null, null, language, stage,
                Map.of(), null, false, 0, null, null);
    }

    /** The same, for a person writing to the company's bot. */
    public static ChatSession startingOnTelegram(String sessionKey, long companyId, long aiAgentId, long telegramBotId,
                                                 String telegramChatId, String language, String stage) {
        return new ChatSession(null, sessionKey, companyId, aiAgentId, null, telegramBotId, telegramChatId,
                language, stage, Map.of(), null, false, 0, null, null);
    }

    /**
     * The same conversation in a language of its own — what a channel that did not ask for
     * one gets: the agent's, settled once the agent has been read.
     */
    public ChatSession withLanguage(String newLanguage) {
        return new ChatSession(id, sessionKey, companyId, aiAgentId, widgetId, telegramBotId, telegramChatId,
                newLanguage, stage, outcome, disposition, ended, messageCount, createdAt, lastMessageAt);
    }

    /** The same conversation after a turn: a new stage, whatever the tools recorded, and more messages. */
    public ChatSession afterTurn(String newStage, Map<String, Object> newOutcome, Disposition newDisposition,
                                 boolean nowEnded, int messagesAdded, Instant at) {
        return new ChatSession(id, sessionKey, companyId, aiAgentId, widgetId, telegramBotId, telegramChatId,
                language, newStage, newOutcome, newDisposition, nowEnded, messageCount + messagesAdded,
                createdAt, at);
    }
}
