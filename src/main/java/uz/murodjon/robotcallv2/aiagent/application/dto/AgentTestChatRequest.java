package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * One turn of a studio test chat ({@code POST /api/ai-agents/{id}/test-chat}). The server keeps
 * nothing between turns: the page sends back what was said and the {@code stage} and
 * {@code outcome} the previous response returned.
 *
 * @param messages   everything said so far, oldest first; empty to start the conversation
 * @param clientText the client's new line; null or blank — the agent greets (only on an empty chat)
 * @param stage      the stage the previous response ended in; null — the scenario's start stage
 * @param outcome    what the agent's tools recorded so far, as the previous response returned it
 * @param variables  the call's dynamic variables ({@code {{clientName}}} …) — the facts a real call
 *                   would carry from its target
 * @param toolMocks  canned answers by tool name; when given, the company tools are never called and
 *                   a tool not listed answers that it is unavailable. Null — the real tools run
 */
public record AgentTestChatRequest(
        @Valid @Size(max = 200) List<AgentTestChatMessage> messages,
        @Size(max = 4000) String clientText,
        String stage,
        Map<String, Object> outcome,
        Map<String, Object> variables,
        Map<String, String> toolMocks
) {
}
