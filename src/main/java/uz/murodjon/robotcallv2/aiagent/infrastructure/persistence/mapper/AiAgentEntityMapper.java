package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.*;
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
public class AiAgentEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(AiAgentEntityMapper.class);
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<DataExtractionField>> DATA_NEEDED_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DataEvaluationCriterion>> DATA_EVALUATION_TYPE = new TypeReference<>() {};
    private static final TypeReference<AgentWebhookConfig> WEBHOOK_TYPE = new TypeReference<>() {};
    private static final TypeReference<ScenarioDefinition> SCENARIO_DEF_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PronunciationRule>> PRONUNCIATION_RULES_TYPE = new TypeReference<>() {};
    private static final TypeReference<AgentGreeting> GREETING_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PostCallAction>> POST_CALL_ACTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Set<HandbookRule>> HANDBOOK_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Double>> SPEED_PER_LANGUAGE_TYPE = new TypeReference<>() {};

    public AiAgentEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiAgent toAiAgent(AiAgentEntity entity) {
        if (entity == null) {
            return null;
        }
        AiAgentSpeechEngineEntity speechEngine = entity.getSpeechEngine();
        AiAgentVoiceEntity voice = entity.getVoice();
        AiAgentAmbienceEntity ambience = entity.getAmbience();
        AiAgentCallBehaviourEntity callBehaviour = entity.getCallBehaviour();
        AiAgentLimitsEntity limits = entity.getLimits();
        AiAgentDataPolicyEntity dataPolicy = entity.getDataPolicy();
        return AiAgent.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .name(entity.getName())
                .description(entity.getDescription())
                .language(entity.getLanguage())
                .channel(entity.getChannel())
                .mode(entity.getAgentMode())
                .persona(entity.getPersona())
                .script(new AiAgentScript(
                        entity.getTemplateId(),
                        entity.getScenarioId(),
                        entity.getScenarioMode(),
                        read(entity.getScenarioDefinition(), SCENARIO_DEF_TYPE, null),
                        entity.getFirstMessage(),
                        entity.getSystemPrompt()))
                .speechEngine(new AiAgentSpeechEngine(
                        speechEngine.getPipelineMode(),
                        speechEngine.getRealtimeProvider(),
                        speechEngine.getPipecatStt(),
                        speechEngine.getPipecatLlm(),
                        speechEngine.getPipecatTts(),
                        speechEngine.getSttProvider(),
                        speechEngine.getSttModel(),
                        speechEngine.getTtsProvider(),
                        speechEngine.getTtsModel()))
                .voice(new AiAgentVoice(
                        voice.getTtsVoice(),
                        voice.getVoiceSpeed(),
                        entity.getLanguageVoiceIds(),
                        read(voice.getVoiceSpeedPerLanguage(), SPEED_PER_LANGUAGE_TYPE, Map.of()),
                        voice.isEmotionAdaptiveVoice(),
                        voice.getSpeakingWarmth()))
                .ambience(new AiAgentAmbience(
                        ambience.getAmbientSound(),
                        ambience.getAmbientSoundVolume(),
                        ambience.getAmbientSoundFadeInSeconds(),
                        ambience.getThinkingSound(),
                        ambience.getThinkingSoundVolume(),
                        ambience.isNoiseCancellationEnabled(),
                        ambience.getNoiseCancellationMode()))
                .callBehaviour(new AiAgentCallBehaviour(
                        callBehaviour.getInterruptionSensitivity(),
                        callBehaviour.isDtmfInputEnabled(),
                        callBehaviour.getVoicemailAction(),
                        callBehaviour.getVoicemailMessage(),
                        callBehaviour.isMidCallSmsEnabled(),
                        callBehaviour.getMidCallSmsTemplate(),
                        callBehaviour.getTransferPhoneNumber(),
                        callBehaviour.getTransferMessage(),
                        callBehaviour.getTransferQueueId(),
                        callBehaviour.getPreConversationAudioFileId(),
                        callBehaviour.getAutoEscalateOnFrustration(),
                        callBehaviour.getFrustrationThreshold()))
                .limits(new AiAgentLimits(
                        limits.getMaxConversationDurationSeconds(),
                        limits.getSilenceEndCallTimeoutSeconds(),
                        limits.getConcurrentCallsLimit(),
                        limits.getDailyCallsLimit()))
                .dataPolicy(new AiAgentDataPolicy(
                        dataPolicy.isZeroPiiRetention(),
                        dataPolicy.isStoreCallAudio(),
                        dataPolicy.getConversationRetentionDays(),
                        dataPolicy.getRealtimeFactsDelivery()))
                .llmModel(entity.getLlmModel())
                .fastLlmModel(entity.getFastLlmModel())
                .temperature(entity.getTemperature())
                .maxOutputTokens(entity.getMaxOutputTokens())
                .useRag(entity.isUseRag())
                .dataNeeded(read(entity.getDataNeeded(), DATA_NEEDED_TYPE, List.of()))
                .dataEvaluation(read(entity.getDataEvaluation(), DATA_EVALUATION_TYPE, List.of()))
                .initiationWebhook(read(entity.getInitiationWebhook(), WEBHOOK_TYPE, null))
                .postCallWebhook(read(entity.getPostCallWebhook(), WEBHOOK_TYPE, null))
                .greeting(read(entity.getGreeting(), GREETING_TYPE, AgentGreeting.DEFAULT))
                .pronunciationRules(read(entity.getPronunciationRules(), PRONUNCIATION_RULES_TYPE, List.of()))
                .postCallActions(read(entity.getPostCallActions(), POST_CALL_ACTIONS_TYPE, List.of()))
                .handbook(read(entity.getHandbook(), HANDBOOK_TYPE, Set.of()))
                .timezone(entity.getTimezone())
                .sipTrunkIds(entity.getSipTrunkIds())
                .enabled(entity.isEnabled())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .version(entity.getVersion())
                .build();
    }

    /** Everything an edit may change; identity and creation stamps stay with the adapter. */
    public void applyEditableFields(AiAgentEntity entity, AiAgent agent, ScenarioEntity scenario,
                                   Set<SipTrunkEntity> sipTrunks, Map<String, TtsVoiceEntity> languageVoices) {
        entity.setName(agent.name());
        entity.setDescription(agent.description());
        entity.setLanguage(agent.language());
        entity.setChannel(agent.channel());
        entity.setAgentMode(agent.mode());
        entity.setPersona(agent.persona());
        applyScript(entity, agent.script(), scenario);
        entity.setSpeechEngine(toSpeechEngineEntity(agent.speechEngine()));
        entity.setVoice(toVoiceEntity(agent.voice()));
        entity.setLanguageVoices(languageVoices);
        entity.setAmbience(toAmbienceEntity(agent.ambience()));
        entity.setCallBehaviour(toCallBehaviourEntity(agent.callBehaviour()));
        entity.setLimits(toLimitsEntity(agent.limits()));
        entity.setDataPolicy(toDataPolicyEntity(agent.dataPolicy()));
        entity.setLlmModel(agent.llmModel());
        entity.setFastLlmModel(agent.fastLlmModel());
        entity.setTemperature(agent.temperature());
        entity.setMaxOutputTokens(agent.maxOutputTokens());
        entity.setUseRag(agent.useRag());
        entity.setDataNeeded(write(agent.dataNeeded(), "[]"));
        entity.setDataEvaluation(write(agent.dataEvaluation(), "[]"));
        entity.setInitiationWebhook(agent.initiationWebhook() != null ? write(agent.initiationWebhook(), null) : null);
        entity.setPostCallWebhook(agent.postCallWebhook() != null ? write(agent.postCallWebhook(), null) : null);
        entity.setGreeting(AgentGreeting.DEFAULT.equals(agent.greeting()) ? null : write(agent.greeting(), null));
        entity.setPronunciationRules(write(agent.pronunciationRules(), "[]"));
        entity.setPostCallActions(write(agent.postCallActions(), "[]"));
        entity.setHandbook(write(agent.handbook(), "[]"));
        entity.setTimezone(agent.timezone());
        entity.setSipTrunks(sipTrunks);
        entity.setEnabled(agent.enabled());
    }

    private void applyScript(AiAgentEntity entity, AiAgentScript script, ScenarioEntity scenario) {
        entity.setTemplateId(script.templateId());
        entity.setScenario(scenario);
        entity.setScenarioMode(script.mode());
        entity.setScenarioDefinition(script.definition() != null ? write(script.definition(), null) : null);
        entity.setFirstMessage(script.firstMessage());
        entity.setSystemPrompt(script.systemPrompt());
    }

    private static AiAgentSpeechEngineEntity toSpeechEngineEntity(AiAgentSpeechEngine speechEngine) {
        return new AiAgentSpeechEngineEntity(
                speechEngine.mode(),
                speechEngine.realtimeProvider(),
                speechEngine.pipecatStt(),
                speechEngine.pipecatLlm(),
                speechEngine.pipecatTts(),
                speechEngine.sttProvider(),
                speechEngine.sttModel(),
                speechEngine.ttsProvider(),
                speechEngine.ttsModel());
    }

    private AiAgentVoiceEntity toVoiceEntity(AiAgentVoice voice) {
        return new AiAgentVoiceEntity(
                voice.defaultVoice(),
                voice.speed(),
                voice.perLanguageSpeed().isEmpty() ? null : write(voice.perLanguageSpeed(), null),
                voice.emotionAdaptive(),
                voice.warmth());
    }

    private static AiAgentAmbienceEntity toAmbienceEntity(AiAgentAmbience ambience) {
        return new AiAgentAmbienceEntity(
                ambience.background(),
                ambience.backgroundVolume(),
                ambience.backgroundFadeInSeconds(),
                ambience.thinking(),
                ambience.thinkingVolume(),
                ambience.noiseCancellationEnabled(),
                ambience.noiseCancellationMode());
    }

    private static AiAgentCallBehaviourEntity toCallBehaviourEntity(AiAgentCallBehaviour behaviour) {
        return new AiAgentCallBehaviourEntity(
                behaviour.interruptionSensitivity(),
                behaviour.dtmfInputEnabled(),
                behaviour.voicemailAction(),
                behaviour.voicemailMessage(),
                behaviour.midCallSmsEnabled(),
                behaviour.midCallSmsTemplate(),
                behaviour.transferPhoneNumber(),
                behaviour.transferMessage(),
                behaviour.transferQueueId(),
                behaviour.preConversationAudioFileId(),
                behaviour.autoEscalateOnFrustration(),
                behaviour.frustrationThreshold());
    }

    private static AiAgentLimitsEntity toLimitsEntity(AiAgentLimits limits) {
        return new AiAgentLimitsEntity(
                limits.maxConversationDurationSeconds(),
                limits.silenceEndCallTimeoutSeconds(),
                limits.concurrentCallsLimit(),
                limits.dailyCallsLimit());
    }

    private static AiAgentDataPolicyEntity toDataPolicyEntity(AiAgentDataPolicy dataPolicy) {
        return new AiAgentDataPolicyEntity(
                dataPolicy.zeroPiiRetention(),
                dataPolicy.storeCallAudio(),
                dataPolicy.conversationRetentionDays(),
                dataPolicy.realtimeFactsDelivery());
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("ai_agent JSON column is not valid, reading fallback: {}", e.getMessage());
            return fallback;
        }
    }

    private String write(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("ai_agent value could not be serialized, storing fallback: {}", e.getMessage());
            return fallback;
        }
    }
}
