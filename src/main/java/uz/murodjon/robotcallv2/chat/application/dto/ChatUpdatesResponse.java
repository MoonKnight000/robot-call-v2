package uz.murodjon.robotcallv2.chat.application.dto;

import java.util.List;

/**
 * What the widget has not shown yet: the company's lines (agent and operator) written after
 * the cursor, and whether a person is answering now.
 */
public record ChatUpdatesResponse(List<WidgetChatMessage> messages, boolean operatorActive, boolean ended) {
}
