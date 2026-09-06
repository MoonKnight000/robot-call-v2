package uz.murodjon.robotcallv2.aiagent.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;

@Component
public class AiAgentMapper {

    public AiAgent toAiAgent(AiAgentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AiAgent(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getDescription(),
                entity.getScenarioId(),
                entity.getLanguage(),
                entity.getTtsVoice(),
                entity.getLanguageVoices(),
                entity.getPersona(),
                entity.getLlmModel(),
                entity.getTemperature(),
                entity.getMaxOutputTokens(),
                entity.getAmbientSound(),
                entity.isEmotionAdaptiveVoice(),
                entity.isDtmfInputEnabled(),
                entity.getVoicemailAction(),
                entity.getVoicemailMessage(),
                entity.isMidCallSmsEnabled(),
                entity.getMidCallSmsTemplate(),
                entity.getSipTrunkIds(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getCreatedBy());
    }

    /** Everything an edit may change; identity and creation stamps stay with the adapter. */
    public void applyEditableFields(AiAgentEntity entity, AiAgent agent) {
        entity.setName(agent.name());
        entity.setDescription(agent.description());
        entity.setLanguage(agent.language());
        entity.setTtsVoice(agent.ttsVoice());
        entity.setLanguageVoices(agent.languageVoicesOrEmpty());
        entity.setPersona(agent.personaOrDefault());
        entity.setLlmModel(agent.llmModel());
        entity.setTemperature(agent.temperature());
        entity.setMaxOutputTokens(agent.maxOutputTokens());
        entity.setAmbientSound(agent.ambientSound());
        entity.setEmotionAdaptiveVoice(agent.emotionAdaptiveVoice());
        entity.setDtmfInputEnabled(agent.dtmfInputEnabled());
        entity.setVoicemailAction(agent.voicemailAction());
        entity.setVoicemailMessage(agent.voicemailMessage());
        entity.setMidCallSmsEnabled(agent.midCallSmsEnabled());
        entity.setMidCallSmsTemplate(agent.midCallSmsTemplate());
        entity.setSipTrunkIds(agent.sipTrunkIdsOrEmpty());
        entity.setEnabled(agent.enabled());
    }
}
