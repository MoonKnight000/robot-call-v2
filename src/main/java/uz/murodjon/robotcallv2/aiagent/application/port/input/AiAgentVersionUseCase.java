package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentVersionRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.PublishAiAgentRequest;

import java.util.List;

/** An agent's published versions: publishing the draft, rolling back, and the history (NEXT-PLAN A.1). */
public interface AiAgentVersionUseCase {

    /** Copies the draft into a new published version; 409 when nothing changed since the last one. */
    AiAgentVersionRow publishAgent(long companyId, long id, PublishAiAgentRequest request);

    /** Makes {@code version} the draft again and publishes it as a new version. */
    AiAgentVersionRow rollbackAgent(long companyId, long id, int version);

    List<AiAgentVersionRow> findVersions(long companyId, long id);
}
