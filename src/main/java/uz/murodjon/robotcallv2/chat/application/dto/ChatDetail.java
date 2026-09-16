package uz.murodjon.robotcallv2.chat.application.dto;

import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * One conversation opened in the panel: its row, what the scenario's tools recorded, and the
 * whole transcript in the order it was written.
 */
public record ChatDetail(
        ChatRow chat,
        Map<String, Object> outcome,
        List<ChatMessage> messages
) {
}
