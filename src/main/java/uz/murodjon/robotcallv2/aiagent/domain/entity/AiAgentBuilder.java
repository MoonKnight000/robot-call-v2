package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Collects an {@link AiAgent}'s fields; obtained from {@link AiAgent#builder()} or, to change a
 * few fields of an existing agent, {@link AiAgent#toBuilder()}. Anything not set is defaulted by
 * the record itself — the language, the persona, each group — so a builder holding only a name
 * still builds a usable agent.
 */
public class AiAgentBuilder {

    private Long id;
    private Long companyId;
    private String name;
    private String description;
    private String language;
    private AgentPersona persona;
    private AiAgentScript script;
    private AiAgentSpeechEngine speechEngine;
    private AiAgentVoice voice;
    private AiAgentAmbience ambience;
    private AiAgentCallBehaviour callBehaviour;
    private AiAgentLimits limits;
    private AiAgentDataPolicy dataPolicy;
    private String llmModel;
    private String fastLlmModel;
    private Double temperature;
    private Integer maxOutputTokens;
    private boolean preemptiveGeneration;
    private boolean ivrNavigationEnabled;
    private boolean useRag;
    private List<DataExtractionField> dataNeeded;
    private List<DataEvaluationCriterion> dataEvaluation;
    private AgentWebhookConfig initiationWebhook;
    private AgentWebhookConfig postCallWebhook;
    private List<PronunciationRule> pronunciationRules;
    private List<PostCallAction> postCallActions;
    private Set<Long> sipTrunkIds;
    private boolean enabled;
    private Instant createdAt;
    private Long createdBy;
    private Integer publishedVersion;
    private long version;

    AiAgentBuilder() {
    }

    AiAgentBuilder(AiAgent source) {
        this.id = source.id();
        this.companyId = source.companyId();
        this.name = source.name();
        this.description = source.description();
        this.language = source.language();
        this.persona = source.persona();
        this.script = source.script();
        this.speechEngine = source.speechEngine();
        this.voice = source.voice();
        this.ambience = source.ambience();
        this.callBehaviour = source.callBehaviour();
        this.limits = source.limits();
        this.dataPolicy = source.dataPolicy();
        this.llmModel = source.llmModel();
        this.fastLlmModel = source.fastLlmModel();
        this.temperature = source.temperature();
        this.maxOutputTokens = source.maxOutputTokens();
        this.preemptiveGeneration = source.preemptiveGeneration();
        this.ivrNavigationEnabled = source.ivrNavigationEnabled();
        this.useRag = source.useRag();
        this.dataNeeded = source.dataNeeded();
        this.dataEvaluation = source.dataEvaluation();
        this.initiationWebhook = source.initiationWebhook();
        this.postCallWebhook = source.postCallWebhook();
        this.pronunciationRules = source.pronunciationRules();
        this.postCallActions = source.postCallActions();
        this.sipTrunkIds = source.sipTrunkIds();
        this.enabled = source.enabled();
        this.createdAt = source.createdAt();
        this.createdBy = source.createdBy();
        this.publishedVersion = source.publishedVersion();
        this.version = source.version();
    }

    public AiAgentBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public AiAgentBuilder companyId(Long companyId) {
        this.companyId = companyId;
        return this;
    }

    public AiAgentBuilder name(String name) {
        this.name = name;
        return this;
    }

    public AiAgentBuilder description(String description) {
        this.description = description;
        return this;
    }

    public AiAgentBuilder language(String language) {
        this.language = language;
        return this;
    }

    public AiAgentBuilder persona(AgentPersona persona) {
        this.persona = persona;
        return this;
    }

    public AiAgentBuilder script(AiAgentScript script) {
        this.script = script;
        return this;
    }

    public AiAgentBuilder speechEngine(AiAgentSpeechEngine speechEngine) {
        this.speechEngine = speechEngine;
        return this;
    }

    public AiAgentBuilder voice(AiAgentVoice voice) {
        this.voice = voice;
        return this;
    }

    public AiAgentBuilder ambience(AiAgentAmbience ambience) {
        this.ambience = ambience;
        return this;
    }

    public AiAgentBuilder callBehaviour(AiAgentCallBehaviour callBehaviour) {
        this.callBehaviour = callBehaviour;
        return this;
    }

    public AiAgentBuilder limits(AiAgentLimits limits) {
        this.limits = limits;
        return this;
    }

    public AiAgentBuilder dataPolicy(AiAgentDataPolicy dataPolicy) {
        this.dataPolicy = dataPolicy;
        return this;
    }

    public AiAgentBuilder llmModel(String llmModel) {
        this.llmModel = llmModel;
        return this;
    }

    public AiAgentBuilder fastLlmModel(String fastLlmModel) {
        this.fastLlmModel = fastLlmModel;
        return this;
    }

    public AiAgentBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public AiAgentBuilder maxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
        return this;
    }

    public AiAgentBuilder preemptiveGeneration(boolean preemptiveGeneration) {
        this.preemptiveGeneration = preemptiveGeneration;
        return this;
    }

    public AiAgentBuilder ivrNavigationEnabled(boolean ivrNavigationEnabled) {
        this.ivrNavigationEnabled = ivrNavigationEnabled;
        return this;
    }

    public AiAgentBuilder useRag(boolean useRag) {
        this.useRag = useRag;
        return this;
    }

    public AiAgentBuilder dataNeeded(List<DataExtractionField> dataNeeded) {
        this.dataNeeded = dataNeeded;
        return this;
    }

    public AiAgentBuilder dataEvaluation(List<DataEvaluationCriterion> dataEvaluation) {
        this.dataEvaluation = dataEvaluation;
        return this;
    }

    public AiAgentBuilder initiationWebhook(AgentWebhookConfig initiationWebhook) {
        this.initiationWebhook = initiationWebhook;
        return this;
    }

    public AiAgentBuilder postCallWebhook(AgentWebhookConfig postCallWebhook) {
        this.postCallWebhook = postCallWebhook;
        return this;
    }

    public AiAgentBuilder pronunciationRules(List<PronunciationRule> pronunciationRules) {
        this.pronunciationRules = pronunciationRules;
        return this;
    }

    public AiAgentBuilder postCallActions(List<PostCallAction> postCallActions) {
        this.postCallActions = postCallActions;
        return this;
    }

    public AiAgentBuilder sipTrunkIds(Set<Long> sipTrunkIds) {
        this.sipTrunkIds = sipTrunkIds;
        return this;
    }

    public AiAgentBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public AiAgentBuilder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public AiAgentBuilder createdBy(Long createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public AiAgentBuilder publishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
        return this;
    }

    public AiAgentBuilder version(long version) {
        this.version = version;
        return this;
    }

    public AiAgent build() {
        return new AiAgent(id, companyId, name, description, language, persona, script, speechEngine, voice,
                ambience, callBehaviour, limits, dataPolicy, llmModel, fastLlmModel, temperature, maxOutputTokens,
                preemptiveGeneration, ivrNavigationEnabled, useRag, dataNeeded, dataEvaluation, initiationWebhook,
                postCallWebhook, pronunciationRules, postCallActions, sipTrunkIds, enabled, createdAt, createdBy,
                publishedVersion, version);
    }
}
