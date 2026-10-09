package uz.murodjon.robotcallv2.copilot.application.port.output;

import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

/** Copilot conversations and their messages. A user only ever reaches their own. */
public interface CopilotRepository {

    CopilotSession createSession(long companyId, CopilotSession session);

    /** The user's conversation, or null when there is none — or it is someone else's. */
    CopilotSession findSessionByCompanyIdAndUserIdAndId(long companyId, long userId, long id);

    PageableData<CopilotSession> findSessionPage(long companyId, long userId, CopilotSessionFilter filter);

    /** Marks the conversation as just used, so it sorts first. */
    void touchSession(long id);

    void deleteSession(long companyId, long userId, long id);

    CopilotMessage createMessage(long companyId, CopilotMessage message);

    /** Every message of the conversation, oldest first. */
    List<CopilotMessage> findMessagesBySessionId(long sessionId);

    void updateMessageCharge(long id, long chargedUzs);
}
