package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An AI agent: the voice, persona, model, speech engine and limits a call is run with.
 *
 * <p>Most of what an agent is arrives in groups — {@link AiAgentVoice}, {@link
 * AiAgentSpeechEngine}, {@link AiAgentAmbience}, {@link AiAgentCallBehaviour}, {@link
 * AiAgentLimits}, {@link AiAgentDataPolicy}, {@link AiAgentScript}. Each of those owns its
 * own defaults, so an agent read from a half-filled row still comes out usable, and each
 * groups settings that are only meaningful together. What stays here is what identifies
 * the agent, what every part of it needs (the language, the persona), and the model.
 */
public record AiAgent(
        Long id,
        Long companyId,
        String name,
        String description,
        String language,
        AgentPersona persona,
        AiAgentScript script,
        AiAgentSpeechEngine speechEngine,
        AiAgentVoice voice,
        AiAgentAmbience ambience,
        AiAgentCallBehaviour callBehaviour,
        AiAgentLimits limits,
        AiAgentDataPolicy dataPolicy,
        String llmModel,
        String fastLlmModel,
        Double temperature,
        Integer maxOutputTokens,
        boolean preemptiveGeneration,
        boolean ivrNavigationEnabled,
        boolean useRag,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        List<PronunciationRule> pronunciationRules,
        List<PostCallAction> postCallActions,
        Set<Long> sipTrunkIds,
        boolean enabled,
        Instant createdAt,
        Long createdBy
) {

    public AiAgent {
        Objects.requireNonNull(name, "Agent name cannot be null");
        if (language == null || language.isBlank()) {
            language = "uz-UZ";
        }
        if (persona == null) {
            persona = AgentPersona.AI_ASSISTANT;
        }
        if (temperature == null) {
            temperature = 0.3;
        }
        if (maxOutputTokens == null) {
            maxOutputTokens = 300;
        }
        // A group is never null: every reader reaches through one, and making them prove
        // it first would put the same null check in every caller instead of here once.
        if (script == null) {
            script = new AiAgentScript(null, null, null, null, null, null);
        }
        if (speechEngine == null) {
            speechEngine = new AiAgentSpeechEngine(null, null, null, null, null, null, null, null, null);
        }
        if (voice == null) {
            voice = new AiAgentVoice(null, null, null, null, null, true);
        }
        if (ambience == null) {
            ambience = new AiAgentAmbience(null, null, null, null, null, true, null);
        }
        if (callBehaviour == null) {
            callBehaviour = new AiAgentCallBehaviour(null, 0, false, null, null, false, null, null, null);
        }
        if (limits == null) {
            limits = AiAgentLimits.NONE;
        }
        if (dataPolicy == null) {
            dataPolicy = new AiAgentDataPolicy(false, true, null);
        }
        sipTrunkIds = sipTrunkIds == null ? Set.of() : Set.copyOf(sipTrunkIds);
        dataNeeded = dataNeeded == null ? List.of() : List.copyOf(dataNeeded);
        dataEvaluation = dataEvaluation == null ? List.of() : List.copyOf(dataEvaluation);
        pronunciationRules = pronunciationRules == null ? List.of() : List.copyOf(pronunciationRules);
        postCallActions = postCallActions == null ? List.of() : List.copyOf(postCallActions);
    }

    /**
     * Whether calls by this agent open with the §11.1 disclosure.
     */
    public boolean disclosureEnabled() {
        return persona == AgentPersona.AI_ASSISTANT;
    }

    /** The voice a call in {@code language} speaks with; this agent's default otherwise. */
    public String voiceFor(String callLanguage) {
        return voice.forLanguage(callLanguage);
    }

    public AiAgent withFirstMessage(String newFirstMessage) {
        return withScript(script.withFirstMessage(newFirstMessage));
    }

    public AiAgent withScenario(AiAgentScript newScript) {
        return withScript(newScript);
    }

    public AiAgent withAnalysis(List<DataExtractionField> newDataNeeded,
                                List<DataEvaluationCriterion> newDataEvaluation) {
        return new AiAgent(
                id, companyId, name, description, language, persona,
                script, speechEngine, voice, ambience, callBehaviour, limits, dataPolicy,
                llmModel, fastLlmModel, temperature, maxOutputTokens,
                preemptiveGeneration, ivrNavigationEnabled, useRag,
                newDataNeeded, newDataEvaluation, initiationWebhook, postCallWebhook,
                pronunciationRules, postCallActions, sipTrunkIds, enabled, createdAt, createdBy);
    }

    public AiAgent withLimits(AiAgentLimits newLimits) {
        return new AiAgent(
                id, companyId, name, description, language, persona,
                script, speechEngine, voice, ambience, callBehaviour, newLimits, dataPolicy,
                llmModel, fastLlmModel, temperature, maxOutputTokens,
                preemptiveGeneration, ivrNavigationEnabled, useRag,
                dataNeeded, dataEvaluation, initiationWebhook, postCallWebhook,
                pronunciationRules, postCallActions, sipTrunkIds, enabled, createdAt, createdBy);
    }

    /** Everything the "advanced" settings screen owns, in one write. */
    public AiAgent withAdvanced(AiAgentAmbience newAmbience,
                                AiAgentCallBehaviour newCallBehaviour,
                                AiAgentDataPolicy newDataPolicy,
                                AiAgentVoice newVoice,
                                boolean newPreemptiveGeneration,
                                boolean newIvrNavigationEnabled,
                                boolean newUseRag,
                                AgentWebhookConfig newInitiationWebhook,
                                AgentWebhookConfig newPostCallWebhook) {
        return new AiAgent(
                id, companyId, name, description, language, persona,
                script, speechEngine, newVoice, newAmbience, newCallBehaviour, limits, newDataPolicy,
                llmModel, fastLlmModel, temperature, maxOutputTokens,
                newPreemptiveGeneration, newIvrNavigationEnabled, newUseRag,
                dataNeeded, dataEvaluation, newInitiationWebhook, newPostCallWebhook,
                pronunciationRules, postCallActions, sipTrunkIds, enabled, createdAt, createdBy);
    }

    public AiAgent withPronunciationRules(List<PronunciationRule> rules) {
        return new AiAgent(
                id, companyId, name, description, language, persona,
                script, speechEngine, voice, ambience, callBehaviour, limits, dataPolicy,
                llmModel, fastLlmModel, temperature, maxOutputTokens,
                preemptiveGeneration, ivrNavigationEnabled, useRag,
                dataNeeded, dataEvaluation, initiationWebhook, postCallWebhook,
                rules, postCallActions, sipTrunkIds, enabled, createdAt, createdBy);
    }

    public AiAgent withPostCallActions(List<PostCallAction> actions) {
        return new AiAgent(
                id, companyId, name, description, language, persona,
                script, speechEngine, voice, ambience, callBehaviour, limits, dataPolicy,
                llmModel, fastLlmModel, temperature, maxOutputTokens,
                preemptiveGeneration, ivrNavigationEnabled, useRag,
                dataNeeded, dataEvaluation, initiationWebhook, postCallWebhook,
                pronunciationRules, actions, sipTrunkIds, enabled, createdAt, createdBy);
    }

    private AiAgent withScript(AiAgentScript newScript) {
        return new AiAgent(
                id, companyId, name, description, language, persona,
                newScript, speechEngine, voice, ambience, callBehaviour, limits, dataPolicy,
                llmModel, fastLlmModel, temperature, maxOutputTokens,
                preemptiveGeneration, ivrNavigationEnabled, useRag,
                dataNeeded, dataEvaluation, initiationWebhook, postCallWebhook,
                pronunciationRules, postCallActions, sipTrunkIds, enabled, createdAt, createdBy);
    }

}
