package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.aiagent.domain.enums.LlmFallbackMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/** JPA entity for ai_agent (V12). */
@Entity
@Table(name = "ai_agent")
@SecondaryTable(name = "ai_agent_ambience", pkJoinColumns = @PrimaryKeyJoinColumn(name = "ai_agent_id", referencedColumnName = "id"))
public class AiAgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private ScenarioEntity scenario;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_id")
    private AgentTemplate templateId;

    @Column(name = "first_message", columnDefinition = "TEXT")
    private String firstMessage;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_mode", nullable = false)
    private ScenarioMode scenarioMode = ScenarioMode.PROMPT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_definition", columnDefinition = "jsonb")
    private String scenarioDefinition;

    @Column(name = "use_rag", nullable = false)
    private boolean useRag;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private AiAgentChannel channel = AiAgentChannel.OMNICHANNEL;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_mode", nullable = false)
    private AiAgentMode agentMode = AiAgentMode.SINGLE_PROMPT;

    @Column(nullable = false)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "speech_languages", columnDefinition = "jsonb")
    private String speechLanguages = "[]";

    @Embedded
    private AiAgentSpeechEngineEntity speechEngine = new AiAgentSpeechEngineEntity();

    @Embedded
    private AiAgentVoiceEntity voice = new AiAgentVoiceEntity();

    @Embedded
    private AiAgentAmbienceEntity ambience = new AiAgentAmbienceEntity();

    @Embedded
    private AiAgentCallBehaviourEntity callBehaviour = new AiAgentCallBehaviourEntity();

    @Embedded
    private AiAgentLimitsEntity limits = new AiAgentLimitsEntity();

    @Embedded
    private AiAgentDataPolicyEntity dataPolicy = new AiAgentDataPolicyEntity();

    // Eager: the mapper reads both collections outside any transaction. SUBSELECT: an agent
    // list loads them in one query each instead of one per agent, and a single load no
    // longer joins the two collections into one cartesian result.
    @ManyToMany(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @JoinTable(name = "ai_agent_voice",
            joinColumns = @JoinColumn(name = "ai_agent_id"),
            inverseJoinColumns = @JoinColumn(name = "voice_id"))
    @MapKeyColumn(name = "language")
    private Map<String, TtsVoiceEntity> languageVoices = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentPersona persona = AgentPersona.AI_ASSISTANT;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "fast_llm_model")
    private String fastLlmModel;

    @Column(name = "fallback_llm_model")
    private String fallbackLlmModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_llm_mode")
    private LlmFallbackMode fallbackLlmMode;

    private Double temperature = 0.65;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens = 300;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_needed", columnDefinition = "jsonb")
    private String dataNeeded = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_evaluation", columnDefinition = "jsonb")
    private String dataEvaluation = "[]";

    @Column(name = "summary_enabled", nullable = false)
    private boolean summaryEnabled = true;

    @Column(name = "sentiment_enabled", nullable = false)
    private boolean sentimentEnabled = true;

    @Column(name = "analysis_model")
    private String analysisModel;

    @Column(name = "memory_enabled", nullable = false)
    private boolean memoryEnabled = true;

    @Column(name = "rag_top_k")
    private Integer ragTopK;

    @Column(name = "rag_min_score")
    private Double ragMinScore;

    @Column(name = "rag_instruction", length = 500)
    private String ragInstruction;

    @Column(name = "redact_phone_numbers", nullable = false)
    private boolean redactPhoneNumbers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_variables", columnDefinition = "jsonb")
    private String defaultVariables = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disabled_integration_tools", columnDefinition = "jsonb")
    private String disabledIntegrationTools = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "functions", columnDefinition = "jsonb")
    private String functions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "phrases", columnDefinition = "jsonb")
    private String phrases;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initiation_webhook", columnDefinition = "jsonb")
    private String initiationWebhook;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_call_webhook", columnDefinition = "jsonb")
    private String postCallWebhook;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "greeting", columnDefinition = "jsonb")
    private String greeting;

    @JdbcTypeCode(SqlTypes.JSON)
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "ai_agent_pronunciation_rule", joinColumns = @JoinColumn(name = "ai_agent_id"))
    private List<PronunciationRuleEmbeddable> pronunciationRules = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_call_actions", columnDefinition = "jsonb")
    private String postCallActions = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pre_call_functions", columnDefinition = "jsonb")
    private String preCallFunctions = "[]";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_agent_handbook_rule", joinColumns = @JoinColumn(name = "ai_agent_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rule", nullable = false)
    private Set<HandbookRule> handbook = EnumSet.noneOf(HandbookRule.class);

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    /** Optimistic lock: only the aiagent adapter writes this row, so every write is an edit. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public Long getCompanyId() {
        return company != null ? company.getId() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ScenarioEntity getScenario() {
        return scenario;
    }

    public void setScenario(ScenarioEntity scenario) {
        this.scenario = scenario;
    }

    public Long getScenarioId() {
        return scenario != null ? scenario.getId() : null;
    }

    public AgentTemplate getTemplateId() {
        return templateId;
    }

    public void setTemplateId(AgentTemplate templateId) {
        this.templateId = templateId;
    }

    public String getFirstMessage() {
        return firstMessage;
    }

    public void setFirstMessage(String firstMessage) {
        this.firstMessage = firstMessage;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public ScenarioMode getScenarioMode() {
        return scenarioMode;
    }

    public void setScenarioMode(ScenarioMode scenarioMode) {
        this.scenarioMode = scenarioMode != null ? scenarioMode : ScenarioMode.PROMPT;
    }

    public String getScenarioDefinition() {
        return scenarioDefinition;
    }

    public void setScenarioDefinition(String scenarioDefinition) {
        this.scenarioDefinition = scenarioDefinition;
    }

    public boolean isUseRag() {
        return useRag;
    }

    public void setUseRag(boolean useRag) {
        this.useRag = useRag;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSpeechLanguages() {
        return speechLanguages;
    }

    public void setSpeechLanguages(String speechLanguages) {
        this.speechLanguages = speechLanguages;
    }

    public Map<String, TtsVoiceEntity> getLanguageVoices() {
        return languageVoices;
    }

    /** The voice ids the agent speaks each language with, keyed the same way. */
    public Map<String, String> getLanguageVoiceIds() {
        Map<String, String> ids = new LinkedHashMap<>();
        languageVoices.forEach((language, voice) -> ids.put(language, voice.getId()));
        return ids;
    }

    /**
     * Replaces the contents, never the map itself: this is a join table, and handing
     * Hibernate a different instance makes it re-insert every row it has just been told to
     * keep — the same (agent, language) key twice in one flush.
     */
    public void setLanguageVoices(Map<String, TtsVoiceEntity> languageVoices) {
        if (languageVoices == this.languageVoices) {
            return;
        }
        this.languageVoices.clear();
        if (languageVoices != null) {
            this.languageVoices.putAll(languageVoices);
        }
    }

    public AgentPersona getPersona() {
        return persona;
    }

    public void setPersona(AgentPersona persona) {
        this.persona = persona != null ? persona : AgentPersona.AI_ASSISTANT;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getFastLlmModel() {
        return fastLlmModel;
    }

    public void setFastLlmModel(String fastLlmModel) {
        this.fastLlmModel = fastLlmModel;
    }

    public String getFallbackLlmModel() {
        return fallbackLlmModel;
    }

    public void setFallbackLlmModel(String fallbackLlmModel) {
        this.fallbackLlmModel = fallbackLlmModel;
    }

    public LlmFallbackMode getFallbackLlmMode() {
        return fallbackLlmMode;
    }

    public void setFallbackLlmMode(LlmFallbackMode fallbackLlmMode) {
        this.fallbackLlmMode = fallbackLlmMode;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getDataNeeded() {
        return dataNeeded;
    }

    public void setDataNeeded(String dataNeeded) {
        this.dataNeeded = dataNeeded != null ? dataNeeded : "[]";
    }

    public boolean isRedactPhoneNumbers() {
        return redactPhoneNumbers;
    }

    public void setRedactPhoneNumbers(boolean redactPhoneNumbers) {
        this.redactPhoneNumbers = redactPhoneNumbers;
    }

    public String getDefaultVariables() {
        return defaultVariables;
    }

    public void setDefaultVariables(String defaultVariables) {
        this.defaultVariables = defaultVariables != null ? defaultVariables : "{}";
    }

    public String getFunctions() {
        return functions;
    }

    public void setFunctions(String functions) {
        this.functions = functions;
    }

    public String getPhrases() {
        return phrases;
    }

    public void setPhrases(String phrases) {
        this.phrases = phrases;
    }

    public String getDisabledIntegrationTools() {
        return disabledIntegrationTools;
    }

    public void setDisabledIntegrationTools(String disabledIntegrationTools) {
        this.disabledIntegrationTools = disabledIntegrationTools != null ? disabledIntegrationTools : "[]";
    }

    public Integer getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(Integer ragTopK) {
        this.ragTopK = ragTopK;
    }

    public Double getRagMinScore() {
        return ragMinScore;
    }

    public void setRagMinScore(Double ragMinScore) {
        this.ragMinScore = ragMinScore;
    }

    public String getRagInstruction() {
        return ragInstruction;
    }

    public void setRagInstruction(String ragInstruction) {
        this.ragInstruction = ragInstruction;
    }

    public boolean isSummaryEnabled() {
        return summaryEnabled;
    }

    public void setSummaryEnabled(boolean summaryEnabled) {
        this.summaryEnabled = summaryEnabled;
    }

    public boolean isSentimentEnabled() {
        return sentimentEnabled;
    }

    public void setSentimentEnabled(boolean sentimentEnabled) {
        this.sentimentEnabled = sentimentEnabled;
    }

    public String getAnalysisModel() {
        return analysisModel;
    }

    public void setAnalysisModel(String analysisModel) {
        this.analysisModel = analysisModel;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    public String getDataEvaluation() {
        return dataEvaluation;
    }

    public void setDataEvaluation(String dataEvaluation) {
        this.dataEvaluation = dataEvaluation != null ? dataEvaluation : "[]";
    }

    public String getInitiationWebhook() {
        return initiationWebhook;
    }

    public void setInitiationWebhook(String initiationWebhook) {
        this.initiationWebhook = initiationWebhook;
    }

    public String getPostCallWebhook() {
        return postCallWebhook;
    }

    public void setPostCallWebhook(String postCallWebhook) {
        this.postCallWebhook = postCallWebhook;
    }

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public List<PronunciationRuleEmbeddable> getPronunciationRules() {
        return pronunciationRules;
    }

    public void setPronunciationRules(List<PronunciationRuleEmbeddable> pronunciationRules) {
        if (pronunciationRules == this.pronunciationRules) {
            return;
        }
        if (this.pronunciationRules == null) {
            this.pronunciationRules = new ArrayList<>();
        } else {
            this.pronunciationRules.clear();
        }
        if (pronunciationRules != null) {
            this.pronunciationRules.addAll(pronunciationRules);
        }
    }

    public String getPostCallActions() {
        return postCallActions;
    }

    public void setPostCallActions(String postCallActions) {
        this.postCallActions = postCallActions;
    }

    public String getPreCallFunctions() {
        return preCallFunctions;
    }

    public void setPreCallFunctions(String preCallFunctions) {
        this.preCallFunctions = preCallFunctions;
    }

    public Set<HandbookRule> getHandbook() {
        return handbook;
    }

    public void setHandbook(Set<HandbookRule> handbook) {
        if (handbook == this.handbook) {
            return;
        }
        if (this.handbook == null) {
            this.handbook = EnumSet.noneOf(HandbookRule.class);
        } else {
            this.handbook.clear();
        }
        if (handbook != null) {
            this.handbook.addAll(handbook);
        }
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public long getVersion() {
        return version;
    }
    public AiAgentSpeechEngineEntity getSpeechEngine() {
        return speechEngine;
    }

    public void setSpeechEngine(AiAgentSpeechEngineEntity speechEngine) {
        this.speechEngine = speechEngine;
    }

    public AiAgentVoiceEntity getVoice() {
        return voice;
    }

    public void setVoice(AiAgentVoiceEntity voice) {
        this.voice = voice;
    }

    public AiAgentAmbienceEntity getAmbience() {
        return ambience;
    }

    public void setAmbience(AiAgentAmbienceEntity ambience) {
        this.ambience = ambience;
    }

    public AiAgentCallBehaviourEntity getCallBehaviour() {
        return callBehaviour;
    }

    public void setCallBehaviour(AiAgentCallBehaviourEntity callBehaviour) {
        this.callBehaviour = callBehaviour;
    }

    /** Never null: an agent whose limit columns are all null loads with no embeddable at all. */
    public AiAgentLimitsEntity getLimits() {
        return limits != null ? limits : new AiAgentLimitsEntity();
    }

    public void setLimits(AiAgentLimitsEntity limits) {
        this.limits = limits;
    }

    public AiAgentDataPolicyEntity getDataPolicy() {
        return dataPolicy;
    }

    public void setDataPolicy(AiAgentDataPolicyEntity dataPolicy) {
        this.dataPolicy = dataPolicy;
    }

    public AiAgentChannel getChannel() {
        return channel;
    }

    public void setChannel(AiAgentChannel channel) {
        this.channel = channel != null ? channel : AiAgentChannel.OMNICHANNEL;
    }

    public AiAgentMode getAgentMode() {
        return agentMode;
    }

    public void setAgentMode(AiAgentMode agentMode) {
        this.agentMode = agentMode != null ? agentMode : AiAgentMode.SINGLE_PROMPT;
    }
}
