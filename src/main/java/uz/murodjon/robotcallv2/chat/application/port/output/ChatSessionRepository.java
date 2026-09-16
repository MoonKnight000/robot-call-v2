package uz.murodjon.robotcallv2.chat.application.port.output;

import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;

import java.time.Instant;
import java.util.List;

public interface ChatSessionRepository {

    /** Inserts a conversation without an id, replaces the one with it. */
    ChatSession save(ChatSession session);

    /** The conversation that handle opens, or null when nothing does. */
    ChatSession findBySessionKey(String sessionKey);

    /**
     * The open conversation this bot is having with this Telegram chat, or null when there is
     * none — a person who writes again after one ended simply starts another.
     */
    ChatSession findOpenByTelegram(long telegramBotId, String telegramChatId);

    /** This company's conversation, or null when it has none by that id. */
    ChatSession findByCompanyIdAndId(long companyId, long id);

    /** One page of this company's conversations, as the panel lists them. */
    List<ChatSession> findAll(long companyId, ChatFilter filter);

    long count(long companyId, ChatFilter filter);

    void addMessage(ChatMessage message);

    /** The whole transcript in the order it was written — what a turn is rebuilt from. */
    List<ChatMessage> findMessages(long chatSessionId);

    /**
     * Deletes whole conversations started before {@code cutoff}, up to {@code batchSize} of
     * them, and answers how many went. Their messages go with them.
     */
    int deleteSessionsCreatedBefore(Instant cutoff, int batchSize);
}
