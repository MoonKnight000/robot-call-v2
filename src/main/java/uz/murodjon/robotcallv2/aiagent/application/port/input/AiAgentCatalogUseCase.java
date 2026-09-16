package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.EngineOptionsResponse;
import uz.murodjon.robotcallv2.aiagent.application.dto.TemplatePresetDto;

import java.util.List;

/** What the agent form offers before any agent exists: the templates and the speech engines this build has. */
public interface AiAgentCatalogUseCase {

    List<TemplatePresetDto> findTemplates(String language);

    EngineOptionsResponse findEngineOptions();
}
