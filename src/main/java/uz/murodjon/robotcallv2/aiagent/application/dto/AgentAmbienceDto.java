package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentAmbience;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;

/**
 * {@link AiAgentAmbience} as the agent endpoints read and write it. On a write a null field
 * keeps what the agent has (or the create default).
 */
public record AgentAmbienceDto(
        AmbientSound background,
        Double backgroundVolume,
        Double backgroundFadeInSeconds,
        AmbientSound thinking,
        Double thinkingVolume,
        Boolean noiseCancellationEnabled,
        NoiseCancellationMode noiseCancellationMode
) {

    public static AgentAmbienceDto of(AiAgentAmbience ambience) {
        return new AgentAmbienceDto(ambience.background(), ambience.backgroundVolume(),
                ambience.backgroundFadeInSeconds(), ambience.thinking(), ambience.thinkingVolume(),
                ambience.noiseCancellationEnabled(), ambience.noiseCancellationMode());
    }
}
