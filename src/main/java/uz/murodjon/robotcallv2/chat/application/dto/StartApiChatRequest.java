package uz.murodjon.robotcallv2.chat.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Opens a conversation from the company's own app ({@code POST /api/v1/chats}).
 *
 * @param agentId  a CHAT or OMNICHANNEL agent of the company
 * @param language uz, ru, uz-UZ ...; null for the agent's
 * @param facts    what the app already knows about the person — name, phone, order number — read by
 *                 the agent like a call's target facts ({{customer_name}} in its prompt); at most 50
 */
public record StartApiChatRequest(
        @NotNull Long agentId,
        String language,
        @Size(max = 50) Map<String, Object> facts
) {
}
