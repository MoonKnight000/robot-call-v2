package uz.murodjon.robotcallv2.chat.application.dto;

import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;

import java.time.Instant;

/**
 * One line of a widget conversation as the browser polls for it.
 *
 * @param id the cursor the next poll passes as {@code afterId}
 */
public record WidgetChatMessage(long id, ChatRole role, String text, Instant createdAt) {
}
