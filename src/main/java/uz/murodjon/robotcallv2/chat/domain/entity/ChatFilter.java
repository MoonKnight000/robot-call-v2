package uz.murodjon.robotcallv2.chat.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * Pagination + filter for POST /api/chats/list.
 *
 * @param aiAgentId only this agent's conversations; null for all
 * @param channel   only this channel; null for both
 * @param ended     true — only finished ones, false — only those not marked finished; null for all
 * @param from      inclusive, by when the conversation was opened
 * @param to        exclusive
 * @param sentiment     only analysed conversations with this sentiment (post-chat analysis)
 * @param needsFollowUp only analysed conversations the person is waiting on (true) or not (false)
 * @param q             free text: a word from the transcript, the person's name or phone, or the chat id
 * @param takenOver     true — only those a company user is answering now, false — only those the agent is
 */
public record ChatFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<ChatTableField, Sort.Direction> orders,
        Long aiAgentId,
        ChatChannel channel,
        Boolean ended,
        Instant from,
        Instant to,
        Sentiment sentiment,
        Boolean needsFollowUp,
        @Size(max = 100) String q,
        Boolean takenOver
) implements FilterInterface<ChatTableField> {

    public ChatFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(ChatTableField.LAST_MESSAGE_AT, Sort.Direction.DESC);
        }
    }
}
