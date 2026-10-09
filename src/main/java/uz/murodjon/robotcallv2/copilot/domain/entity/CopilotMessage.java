package uz.murodjon.robotcallv2.copilot.domain.entity;

import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;

import java.time.Instant;

/**
 * One line of a copilot conversation.
 *
 * @param content        the text said; for a TOOL row, what the tool answered (cut short when long)
 * @param toolName       TOOL only: which tool was called
 * @param toolArguments  TOOL only: the arguments, as JSON
 * @param toolError      TOOL only: the tool refused (no permission, bad arguments, not found)
 * @param promptTokens   ASSISTANT only: tokens the reply read, its tool rounds included
 * @param chargedUzs     ASSISTANT only: what the reply was charged
 */
public record CopilotMessage(
        long id,
        long sessionId,
        CopilotMessageRole role,
        String content,
        String toolName,
        String toolArguments,
        boolean toolError,
        long promptTokens,
        long completionTokens,
        long chargedUzs,
        Instant createdAt
) {

    public static CopilotMessage user(long sessionId, String content) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.USER, content, null, null, false, 0, 0, 0, null);
    }

    public static CopilotMessage assistant(long sessionId, CopilotModelReply reply) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.ASSISTANT, reply.text(), null, null, false,
                reply.promptTokens(), reply.completionTokens(), 0, null);
    }

    public static CopilotMessage tool(long sessionId, String name, String arguments, String answer, boolean error) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.TOOL, answer, name, arguments, error, 0, 0, 0, null);
    }
}
