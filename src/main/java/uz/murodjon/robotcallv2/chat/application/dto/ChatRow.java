package uz.murodjon.robotcallv2.chat.application.dto;

import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
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
 * @param operatorUserId the user answering it in person, or null while the agent is
 * @param clientName      the person's name when the conversation recorded one (CRM lookup, app facts)
 * @param clientPhone     the person's number in E.164 when they shared one
 * @param lastMessageText the latest line of the transcript, cut to a preview; null before any
 * @param lastMessageRole who wrote it — CLIENT means the person is the one waiting
 * @param sentiment       from the post-chat analysis; null until it ran
 * @param needsFollowUp   from the post-chat analysis; null until it ran
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
        Instant lastMessageAt,
        Long operatorUserId,
        String clientName,
        String clientPhone,
        String lastMessageText,
        ChatRole lastMessageRole,
        Sentiment sentiment,
        Boolean needsFollowUp
) {
}
