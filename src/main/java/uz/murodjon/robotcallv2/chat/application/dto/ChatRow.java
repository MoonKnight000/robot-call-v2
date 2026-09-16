package uz.murodjon.robotcallv2.chat.application.dto;

import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/**
 * A conversation as the panel lists it. The {@code sessionKey} is not here: it is the
 * visitor's handle for writing into the conversation, and the panel only reads.
 *
 * @param stage       the scenario stage the conversation stands in
 * @param disposition how it ended; null while it has not
 * @param ended       set when the agent closed it or it ran out of messages — a conversation
 *                    that simply went quiet stays false
 */
public record ChatRow(
        long id,
        long agentId,
        String agentName,
        ChatChannel channel,
        Long widgetId,
        Long telegramBotId,
        String language,
        String stage,
        Disposition disposition,
        boolean ended,
        int messageCount,
        Instant createdAt,
        Instant lastMessageAt
) {
}
