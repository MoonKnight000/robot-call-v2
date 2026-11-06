package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentExportFile;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentImportResponse;

/** An agent to a file and back: moving a configuration between companies or servers. */
public interface AiAgentPortabilityUseCase {

    AgentExportFile exportAgent(long companyId, long id);

    AgentImportResponse importAgent(long companyId, AgentExportFile file);
}
