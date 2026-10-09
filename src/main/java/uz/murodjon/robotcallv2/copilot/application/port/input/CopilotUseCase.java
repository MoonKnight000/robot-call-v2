package uz.murodjon.robotcallv2.copilot.application.port.input;

import uz.murodjon.robotcallv2.copilot.application.dto.AskCopilotRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.CopilotReplyResponse;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

/**
 * The panel's AI copilot (gap analysis 3.9): answers questions about the company's agents and
 * calls with the platform's own MCP tools, and writes agent changes to drafts only. Every
 * conversation belongs to the user who asked.
 */
public interface CopilotUseCase {

    /** One question; a new conversation when {@code request.sessionId()} is null. */
    CopilotReplyResponse ask(long companyId, AskCopilotRequest request);

    PageableData<CopilotSession> filterSessions(long companyId, CopilotSessionFilter filter);

    /** The conversation in full, tool calls included. */
    List<CopilotMessage> findMessages(long companyId, long sessionId);

    void deleteSession(long companyId, long sessionId);
}
