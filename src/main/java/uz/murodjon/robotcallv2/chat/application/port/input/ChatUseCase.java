package uz.murodjon.robotcallv2.chat.application.port.input;

import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.ChatUpdatesResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;

import java.time.Instant;

/**
 * The same agent, carried in text (UNIVERSAL-OPERATOR-PLAN E.4): one scenario, one tool
 * layer, one set of guardrails, whether the visitor speaks or types.
 */
public interface ChatUseCase {

    /**
     * Opens a conversation for a visitor on a page carrying {@code widgetKey} and answers
     * with the agent's opening line. Public: the origin is checked against the widget's own
     * list and the agent's day has to have room.
     *
     * @param language the visitor's preferred language, or null for the agent's own
     */
    StartChatResponse startChat(String widgetKey, String origin, String language);

    /** Answers one message in an open conversation. */
    ChatReplyResponse sendMessage(String widgetKey, String origin, String sessionKey,
                                  SendChatMessageRequest request);

    /**
     * The company's lines written after {@code afterId} — how a widget shows what an operator
     * wrote into a conversation they took over. Same origin check as {@link #sendMessage}.
     */
    ChatUpdatesResponse findNewMessages(String widgetKey, String origin, String sessionKey, long afterId);

    /** Retention: whole conversations started before {@code cutoff} go, transcripts and all. */
    int deleteSessionsCreatedBefore(Instant cutoff, int batchSize);
}
