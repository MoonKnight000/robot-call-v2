package uz.murodjon.robotcallv2.aiagent.application.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.SipTrunkEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps between {@link AiAgentEntity} and {@link AiAgent}.
 */
@Component
public class AiAgentMapper {

    private static final Logger log = LoggerFactory.getLogger(AiAgentMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<List<DataExtractionField>> DATA_NEEDED_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DataEvaluationCriterion>> DATA_EVALUATION_TYPE = new TypeReference<>() {};
    private static final TypeReference<AgentWebhookConfig> WEBHOOK_TYPE = new TypeReference<>() {};
    private static final TypeReference<ScenarioDefinition> SCENARIO_DEF_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PronunciationRule>> PRONUNCIATION_RULES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PostCallAction>> POST_CALL_ACTIONS_TYPE = new TypeReference<>() {};

    public AiAgent toAiAgent(AiAgentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AiAgent(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getDescription(),
                entity.getLanguage(),
                entity.getPersona(),
                new AiAgentScript(
                        entity.getTemplateId(),
                        entity.getScenarioId(),
                        entity.getScenarioMode(),
                        read(entity.getScenarioDefinition(), SCENARIO_DEF_TYPE, null),
                        entity.getFirstMessage(),
                        entity.getSystemPrompt()),
                new AiAgentSpeechEngine(
                        entity.getPipelineMode(),
                        entity.getRealtimeProvider(),
                        entity.getPipecatStt(),
                        entity.getPipecatLlm(),
                        entity.getPipecatTts(),
                        entity.getSttProvider(),
                        entity.getSttModel(),
                        entity.getTtsProvider(),
                        entity.getTtsModel()),
                new AiAgentVoice(
                        entity.getTtsVoice(),
                        entity.getVoiceSpeed(),
                        entity.getVoiceStability(),
                        entity.getVoiceSimilarityBoost(),
                        entity.getLanguageVoiceIds(),
                        entity.isEmotionAdaptiveVoice()),
                new AiAgentAmbience(
                        entity.getAmbientSound(),
                        entity.getAmbientSoundVolume(),
                        entity.getAmbientSoundFadeInSeconds(),
                        entity.getThinkingSound(),
                        entity.getThinkingSoundVolume(),
                        entity.isNoiseCancellationEnabled(),
                        entity.getNoiseCancellationMode()),
                new AiAgentCallBehaviour(
                        entity.getInterruptionSensitivity(),
                        entity.getEndpointingDelayMs(),
                        entity.isDtmfInputEnabled(),
                        entity.getVoicemailAction(),
                        entity.getVoicemailMessage(),
                        entity.isMidCallSmsEnabled(),
                        entity.getMidCallSmsTemplate(),
                        entity.getTransferPhoneNumber(),
                        entity.getTransferMessage()),
                new AiAgentLimits(
                        entity.getMaxConversationDurationSeconds(),
                        entity.getSilenceEndCallTimeoutSeconds(),
                        entity.getTurnTimeoutSeconds(),
                        entity.getConcurrentCallsLimit(),
                        entity.getDailyCallsLimit()),
                new AiAgentDataPolicy(
                        entity.isZeroPiiRetention(),
                        entity.isStoreCallAudio(),
                        entity.getConversationRetentionDays()),
                entity.getLlmModel(),
                entity.getFastLlmModel(),
                entity.getTemperature(),
                entity.getMaxOutputTokens(),
                entity.isPreemptiveGeneration(),
                entity.isIvrNavigationEnabled(),
                entity.isUseRag(),
                read(entity.getDataNeeded(), DATA_NEEDED_TYPE, List.of()),
                read(entity.getDataEvaluation(), DATA_EVALUATION_TYPE, List.of()),
                read(entity.getInitiationWebhook(), WEBHOOK_TYPE, null),
                read(entity.getPostCallWebhook(), WEBHOOK_TYPE, null),
                read(entity.getPronunciationRules(), PRONUNCIATION_RULES_TYPE, List.of()),
                read(entity.getPostCallActions(), POST_CALL_ACTIONS_TYPE, List.of()),
                entity.getSipTrunkIds(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getCreatedBy());
    }

    /** Everything an edit may change; identity and creation stamps stay with the adapter. */
    public void applyEditableFields(AiAgentEntity entity, AiAgent agent, ScenarioEntity scenario,
                                   Set<SipTrunkEntity> sipTrunks, Map<String, TtsVoiceEntity> languageVoices) {
        entity.setName(agent.name());
        entity.setDescription(agent.description());
        entity.setLanguage(agent.language());
        entity.setPersona(agent.persona());
        applyScript(entity, agent.script(), scenario);
        applySpeechEngine(entity, agent.speechEngine());
        applyVoice(entity, agent.voice(), languageVoices);
        applyAmbience(entity, agent.ambience());
        applyCallBehaviour(entity, agent.callBehaviour());
        applyLimits(entity, agent.limits());
        applyDataPolicy(entity, agent.dataPolicy());
        entity.setLlmModel(agent.llmModel());
        entity.setFastLlmModel(agent.fastLlmModel());
        entity.setTemperature(agent.temperature());
        entity.setMaxOutputTokens(agent.maxOutputTokens());
        entity.setPreemptiveGeneration(agent.preemptiveGeneration());
        entity.setIvrNavigationEnabled(agent.ivrNavigationEnabled());
        entity.setUseRag(agent.useRag());
        entity.setDataNeeded(write(agent.dataNeeded(), "[]"));
        entity.setDataEvaluation(write(agent.dataEvaluation(), "[]"));
        entity.setInitiationWebhook(agent.initiationWebhook() != null ? write(agent.initiationWebhook(), null) : null);
        entity.setPostCallWebhook(agent.postCallWebhook() != null ? write(agent.postCallWebhook(), null) : null);
        entity.setPronunciationRules(write(agent.pronunciationRules(), "[]"));
        entity.setPostCallActions(write(agent.postCallActions(), "[]"));
        entity.setSipTrunks(sipTrunks);
        entity.setEnabled(agent.enabled());
    }

    private static void applyScript(AiAgentEntity entity, AiAgentScript script, ScenarioEntity scenario) {
        entity.setTemplateId(script.templateId());
        entity.setScenario(scenario);
        entity.setScenarioMode(script.mode());
        entity.setScenarioDefinition(script.definition() != null ? write(script.definition(), null) : null);
        entity.setFirstMessage(script.firstMessage());
        entity.setSystemPrompt(script.systemPrompt());
    }

    private static void applySpeechEngine(AiAgentEntity entity, AiAgentSpeechEngine speechEngine) {
        entity.setPipelineMode(speechEngine.mode());
        entity.setRealtimeProvider(speechEngine.realtimeProvider());
        entity.setPipecatStt(speechEngine.pipecatStt());
        entity.setPipecatLlm(speechEngine.pipecatLlm());
        entity.setPipecatTts(speechEngine.pipecatTts());
        entity.setSttProvider(speechEngine.sttProvider());
        entity.setSttModel(speechEngine.sttModel());
        entity.setTtsProvider(speechEngine.ttsProvider());
        entity.setTtsModel(speechEngine.ttsModel());
    }

    private static void applyVoice(AiAgentEntity entity, AiAgentVoice voice,
                                   Map<String, TtsVoiceEntity> languageVoices) {
        entity.setTtsVoice(voice.defaultVoice());
        entity.setVoiceSpeed(voice.speed());
        entity.setVoiceStability(voice.stability());
        entity.setVoiceSimilarityBoost(voice.similarityBoost());
        entity.setLanguageVoices(languageVoices);
        entity.setEmotionAdaptiveVoice(voice.emotionAdaptive());
    }

    private static void applyAmbience(AiAgentEntity entity, AiAgentAmbience ambience) {
        entity.setAmbientSound(ambience.background());
        entity.setAmbientSoundVolume(ambience.backgroundVolume());
        entity.setAmbientSoundFadeInSeconds(ambience.backgroundFadeInSeconds());
        entity.setThinkingSound(ambience.thinking());
        entity.setThinkingSoundVolume(ambience.thinkingVolume());
        entity.setNoiseCancellationEnabled(ambience.noiseCancellationEnabled());
        entity.setNoiseCancellationMode(ambience.noiseCancellationMode());
    }

    private static void applyCallBehaviour(AiAgentEntity entity, AiAgentCallBehaviour behaviour) {
        entity.setInterruptionSensitivity(behaviour.interruptionSensitivity());
        entity.setEndpointingDelayMs(behaviour.endpointingDelayMs());
        entity.setDtmfInputEnabled(behaviour.dtmfInputEnabled());
        entity.setVoicemailAction(behaviour.voicemailAction());
        entity.setVoicemailMessage(behaviour.voicemailMessage());
        entity.setMidCallSmsEnabled(behaviour.midCallSmsEnabled());
        entity.setMidCallSmsTemplate(behaviour.midCallSmsTemplate());
        entity.setTransferPhoneNumber(behaviour.transferPhoneNumber());
        entity.setTransferMessage(behaviour.transferMessage());
    }

    private static void applyLimits(AiAgentEntity entity, AiAgentLimits limits) {
        entity.setMaxConversationDurationSeconds(limits.maxConversationDurationSeconds());
        entity.setSilenceEndCallTimeoutSeconds(limits.silenceEndCallTimeoutSeconds());
        entity.setTurnTimeoutSeconds(limits.turnTimeoutSeconds());
        entity.setConcurrentCallsLimit(limits.concurrentCallsLimit());
        entity.setDailyCallsLimit(limits.dailyCallsLimit());
    }

    private static void applyDataPolicy(AiAgentEntity entity, AiAgentDataPolicy dataPolicy) {
        entity.setZeroPiiRetention(dataPolicy.zeroPiiRetention());
        entity.setStoreCallAudio(dataPolicy.storeCallAudio());
        entity.setConversationRetentionDays(dataPolicy.conversationRetentionDays());
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            log.warn("ai_agent JSON column is not valid, reading fallback: {}", e.getMessage());
            return fallback;
        }
    }

    private static String write(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("ai_agent value could not be serialized, storing fallback: {}", e.getMessage());
            return fallback;
        }
    }
}
