package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.StudioTestChat;
import uz.murodjon.robotcallv2.agent.dialog.StudioTestTurn;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatMessage;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatResponse;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentTestChatUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.TestChatRole;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * The studio's "LLM test" on the agent's draft — what was last saved, as the voice test does.
 * The turn itself is {@link StudioTestChat}: the real text turn, not a stand-in prompt.
 */
@Service
public class AgentTestChatService implements AgentTestChatUseCase {

    private final AiAgentUseCase aiAgentUseCase;
    private final StudioTestChat studioTestChat;

    public AgentTestChatService(AiAgentUseCase aiAgentUseCase, StudioTestChat studioTestChat) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.studioTestChat = studioTestChat;
    }

    @Override
    public AgentTestChatResponse replyTestChat(long companyId, long id, AgentTestChatRequest request) {
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, id);
        List<AgentTestChatMessage> messages = request.messages() != null ? request.messages() : List.of();
        String clientText = request.clientText() == null || request.clientText().isBlank()
                ? null : request.clientText().trim();
        // Only an empty chat may be opened by the agent; later, every turn answers the client.
        if (clientText == null && !messages.isEmpty()) {
            throw new ValidationException(ErrorCode.AGENT_TEST_CHAT_TEXT_REQUIRED);
        }
        if (!studioTestChat.available()) {
            throw new ConflictException(ErrorCode.AGENT_TEST_CHAT_UNAVAILABLE);
        }
        List<Message> history = new ArrayList<>();
        for (AgentTestChatMessage message : messages) {
            history.add(message.role() == TestChatRole.AGENT
                    ? new AssistantMessage(message.text())
                    : new UserMessage(message.text()));
        }

        long start = System.nanoTime();
        StudioTestTurn turn = studioTestChat.reply(companyId, agent,
                aiAgentUseCase.resolveScenario(companyId, agent).definition(), history, clientText,
                request.stage(), request.outcome(), request.variables(), request.toolMocks());
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new AgentTestChatResponse(turn.reply(), turn.stage(), turn.outcome(), turn.toolCalls(),
                turn.violations(), turn.ended(), turn.disposition(), latencyMs);
    }
}
