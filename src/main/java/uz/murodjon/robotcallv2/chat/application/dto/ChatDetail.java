package uz.murodjon.robotcallv2.chat.application.dto;

import uz.murodjon.robotcallv2.chat.domain.entity.ChatAnalysis;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * One conversation opened in the panel: its row, what the scenario's tools recorded, the
 * whole transcript in the order it was written, and the post-chat analysis.
 *
 * @param analysis null until the chat has ended (or fell silent) and was analysed
 */
public record ChatDetail(
        ChatRow chat,
        Map<String, Object> outcome,
        List<ChatMessage> messages,
        ChatAnalysis analysis
) {
}
