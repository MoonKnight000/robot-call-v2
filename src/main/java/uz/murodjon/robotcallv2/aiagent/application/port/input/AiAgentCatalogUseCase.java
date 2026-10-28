package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.EngineOptionsResponse;
import uz.murodjon.robotcallv2.aiagent.application.dto.TemplatePresetDto;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;

import java.util.List;

/** What the agent form offers before any agent exists: the templates and the speech engines this build has. */
public interface AiAgentCatalogUseCase {

    List<TemplatePresetDto> findTemplates(String language);

    EngineOptionsResponse findEngineOptions();

    /** The profiles this build can run in full, in the configured order. */
    List<EngineProfile> findEngineProfiles();

    /** The profile {@code id} names; a {@code ValidationException} when this build does not offer it. */
    EngineProfile requireEngineProfile(String id);
}
