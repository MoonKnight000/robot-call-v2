package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentConversion;

/** @param target what the new copy of the agent becomes */
public record ConvertAiAgentRequest(
        @NotNull AiAgentConversion target
) {
}
