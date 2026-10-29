package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatResponse;

/** The studio's "LLM test": a typed conversation with the agent's draft, turn by turn. */
public interface AgentTestChatUseCase {

    AgentTestChatResponse replyTestChat(long companyId, long id, AgentTestChatRequest request);
}
