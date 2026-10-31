package uz.murodjon.robotcallv2.copilot.application.port.input;

import uz.murodjon.robotcallv2.copilot.application.dto.AskCopilotRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.CopilotReplyResponse;
import uz.murodjon.robotcallv2.copilot.application.dto.RewritePromptRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.RewritePromptResponse;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.util.List;

/**
 * The panel's AI copilot (gap analysis 3.9): answers questions about the company's agents and
 * calls with the platform's own MCP tools, and writes agent changes to drafts only. Every
 * conversation belongs to the user who asked.
 */
public interface CopilotUseCase {

    /** One question; a new conversation when {@code request.sessionId()} is null. */
    CopilotReplyResponse ask(long companyId, AskCopilotRequest request);

    /** One question; streams events (session, tool_call, tool_result, delta, done, error) via sink. */
    EventSubscription askStream(long companyId, AskCopilotRequest request, EventSink sink);

    PageableData<CopilotSession> filterSessions(long companyId, CopilotSessionFilter filter);

    /** The conversation in full, tool calls included. */
    List<CopilotMessage> findMessages(long companyId, long sessionId);

    void deleteSession(long companyId, long sessionId);

    /** The prompt editors' AI button: improves or writes one prompt. Nothing is saved. */
    RewritePromptResponse rewritePrompt(long companyId, RewritePromptRequest request);
}
