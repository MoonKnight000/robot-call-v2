package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.util.Map;
import java.util.Set;

/**
 * Full edit of an agent ({@code PUT /api/ai-agents/{id}}) — the same shape as
 * {@link CreateAiAgentRequest}. The scenario may be changed here: an agent is a way of
 * speaking, and pointing it at a different script does not make it a different agent.
 * Campaigns already running under it pick the new script up on their next call.
 */
public record UpdateAiAgentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        @NotNull Long scenarioId,
        String language,
        String ttsVoice,
        Map<String, String> languageVoices,
        AgentPersona persona,
        @Size(max = 120) String llmModel,
        Double temperature,
        Integer maxOutputTokens,
        AmbientSound ambientSound,
        Boolean emotionAdaptiveVoice,
        Boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        @Size(max = 500) String voicemailMessage,
        Boolean midCallSmsEnabled,
        @Size(max = 500) String midCallSmsTemplate,
        Set<Long> sipTrunkIds,
        Boolean enabled) {
}
