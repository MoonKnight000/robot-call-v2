package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * {@link AiAgent} with the names behind its ids resolved, for the agent list and detail
 * pages — a projection over {@code ai_agent} and {@code scenario}, so it takes the
 * {@code <Noun>Row} suffix rather than bare {@code AiAgent}.
 */
public record AiAgentRow(
        long id,
        long companyId,
        String name,
        String description,
        long scenarioId,
        String scenarioName,
        String language,
        String ttsVoice,
        Map<String, String> languageVoices,
        AgentPersona persona,
        boolean disclosureEnabled,
        String llmModel,
        Double temperature,
        Integer maxOutputTokens,
        AmbientSound ambientSound,
        boolean emotionAdaptiveVoice,
        boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        Set<Long> sipTrunkIds,
        boolean enabled,
        Instant createdAt,
        Long createdBy,
        String createdByName) {

    public static AiAgentRow of(AiAgent agent, String scenarioName, String createdByName) {
        return new AiAgentRow(
                agent.id(), agent.companyId(), agent.name(), agent.description(),
                agent.scenarioId(), scenarioName, agent.language(), agent.ttsVoice(),
                agent.languageVoicesOrEmpty(), agent.personaOrDefault(), agent.disclosureEnabled(),
                agent.llmModel(), agent.temperature(), agent.maxOutputTokens(),
                agent.ambientSound(), agent.emotionAdaptiveVoice(), agent.dtmfInputEnabled(),
                agent.voicemailAction(), agent.voicemailMessage(),
                agent.midCallSmsEnabled(), agent.midCallSmsTemplate(),
                agent.sipTrunkIdsOrEmpty(), agent.enabled(), agent.createdAt(),
                agent.createdBy(), createdByName);
    }
}
