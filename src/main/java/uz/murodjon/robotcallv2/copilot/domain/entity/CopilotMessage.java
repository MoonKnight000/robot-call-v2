package uz.murodjon.robotcallv2.copilot.domain.entity;

import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotProposalStatus;

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
 * @param proposalStatus TOOL only: set when the call was a change kept for the user to apply; null
 *                       for a tool that ran at once
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
        Instant createdAt,
        CopilotProposalStatus proposalStatus
) {

    public static CopilotMessage user(long sessionId, String content) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.USER, content, null, null, false, 0, 0, 0, null, null);
    }

    public static CopilotMessage assistant(long sessionId, CopilotModelReply reply) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.ASSISTANT, reply.text(), null, null, false,
                reply.promptTokens(), reply.completionTokens(), 0, null, null);
    }

    public static CopilotMessage tool(long sessionId, String name, String arguments, String answer, boolean error) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.TOOL, answer, name, arguments, error, 0, 0, 0,
                null, null);
    }

    /** A change the copilot wants to make, kept until the user applies or rejects it. */
    public static CopilotMessage proposal(long sessionId, String name, String arguments) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.TOOL, "", name, arguments, false, 0, 0, 0,
                null, CopilotProposalStatus.PENDING);
    }

    /** What the user decided about a proposal, in words the model reads with the next question. */
    public static CopilotMessage note(long sessionId, String content) {
        return new CopilotMessage(0, sessionId, CopilotMessageRole.NOTE, content, null, null, false, 0, 0, 0,
                null, null);
    }

    public boolean isPendingProposal() {
        return proposalStatus == CopilotProposalStatus.PENDING;
    }

    public CopilotMessage withProposalOutcome(CopilotProposalStatus status, String answer, boolean error) {
        return new CopilotMessage(id, sessionId, role, answer, toolName, toolArguments, error, promptTokens,
                completionTokens, chargedUzs, createdAt, status);
    }

    public CopilotMessage withCharge(long charged) {
        return new CopilotMessage(id, sessionId, role, content, toolName, toolArguments, toolError, promptTokens,
                completionTokens, charged, createdAt, proposalStatus);
    }
}
