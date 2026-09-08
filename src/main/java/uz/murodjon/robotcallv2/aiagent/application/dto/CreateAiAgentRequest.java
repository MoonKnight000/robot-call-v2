package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.enums.*;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A new AI agent ({@code POST /api/ai-agents}).
 */
public record CreateAiAgentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        Long scenarioId,
        AgentTemplate templateId,
        String firstMessage,
        String systemPrompt,
        ScenarioMode scenarioMode,
        ScenarioDefinition scenarioDefinition,
        Boolean preemptiveGeneration,
        Boolean ivrNavigationEnabled,
        Boolean useRag,
        String language,
        PipelineMode pipelineMode,
        String realtimeProvider,
        String pipecatStt,
        String pipecatLlm,
        String pipecatTts,
        String sttProvider,
        String sttModel,
        String ttsProvider,
        String ttsModel,
        String ttsVoice,
        Double voiceSpeed,
        Double voiceStability,
        Double voiceSimilarityBoost,
        Map<String, String> languageVoices,
        AgentPersona persona,
        @Size(max = 120) String llmModel,
        @Size(max = 120) String fastLlmModel,
        Double temperature,
        Integer maxOutputTokens,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation,
        Boolean zeroPiiRetention,
        Boolean storeCallAudio,
        Integer conversationRetentionDays,
        Integer maxConversationDurationSeconds,
        Integer silenceEndCallTimeoutSeconds,
        Integer turnTimeoutSeconds,
        Integer concurrentCallsLimit,
        Integer dailyCallsLimit,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        AmbientSound ambientSound,
        Double ambientSoundVolume,
        Double ambientSoundFadeInSeconds,
        AmbientSound thinkingSound,
        Double thinkingSoundVolume,
        Boolean noiseCancellationEnabled,
        NoiseCancellationMode noiseCancellationMode,
        Boolean emotionAdaptiveVoice,
        Boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        Boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        String transferPhoneNumber,
        String transferMessage,
        InterruptionSensitivity interruptionSensitivity,
        Integer endpointingDelayMs,
        Set<Long> sipTrunkIds,
        Boolean enabled
) {
}
