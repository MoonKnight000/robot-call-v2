package uz.murodjon.robotcallv2.chat.domain.entity;

import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;

import java.time.Instant;

/**
 * One line of a chat, stored in the order it was written.
 *
 * <p>The transcript is not only what a person reads back: it is what the conversation is
 * rebuilt from before the next message is answered, so a line that is not here did not
 * happen as far as the agent is concerned.
 */
public record ChatMessage(Long id, long chatSessionId, ChatRole role, String text, Instant createdAt) {

    public static ChatMessage written(long chatSessionId, ChatRole role, String text) {
        return new ChatMessage(null, chatSessionId, role, text, null);
    }
}
