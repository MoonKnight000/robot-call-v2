package uz.murodjon.robotcallv2.copilot.application.port.output;

import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotModelReply;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;

/** The chat model behind the copilot. */
public interface CopilotModelPort {

    /**
     * Answers the conversation's last question, calling tools through {@code invoker} as many
     * rounds as the model needs.
     *
     * @param history         USER and ASSISTANT messages, oldest first; the last one is the question
     * @param reasoningEffort how hard the model thinks (the question's mode)
     * @throws uz.murodjon.robotcallv2.shared.exception.ConflictException when no model is configured
     * @throws uz.murodjon.robotcallv2.shared.exception.ExternalServiceException when the model fails
     */
    CopilotModelReply generateReply(String instructions, List<CopilotMessage> history, List<McpTool> tools,
                                    ReasoningEffort reasoningEffort, CopilotToolInvoker invoker);

    /**
     * One answer to one message, without tools or history.
     *
     * @throws uz.murodjon.robotcallv2.shared.exception.ConflictException when no model is configured
     * @throws uz.murodjon.robotcallv2.shared.exception.ExternalServiceException when the model fails
     */
    CopilotModelReply generateText(String instructions, String message);
}
