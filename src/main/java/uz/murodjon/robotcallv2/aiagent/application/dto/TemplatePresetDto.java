package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.TemplateDefaults;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;

/**
 * Template preset descriptor for frontend template picker.
 */
public record TemplatePresetDto(
        AgentTemplate templateId,
        String name,
        String description,
        TemplateDefaults defaults
) {
}
