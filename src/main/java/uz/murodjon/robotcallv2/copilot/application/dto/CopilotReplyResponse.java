package uz.murodjon.robotcallv2.copilot.application.dto;

import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;

import java.util.List;

/**
 * @param sessionId the conversation — the new one's id when the question started it
 * @param reply     the copilot's answer, with the tokens it used and what it was charged
 * @param toolCalls the tools it called on the way, in order
 */
public record CopilotReplyResponse(long sessionId, CopilotMessage reply, List<CopilotMessage> toolCalls) {
}
