package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.SipTrunkEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** JPA entity for ai_agent (V12). */
@Entity
@Table(name = "ai_agent")
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

    private Double temperature = 0.3;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens = 300;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_needed", columnDefinition = "jsonb")
    private String dataNeeded = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_evaluation", columnDefinition = "jsonb")
    private String dataEvaluation = "[]";

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
    @Column(name = "pronunciation_rules", columnDefinition = "jsonb")
    private String pronunciationRules = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_call_actions", columnDefinition = "jsonb")
    private String postCallActions = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "handbook", columnDefinition = "jsonb")
    private String handbook = "[]";

    @Column(name = "timezone", length = 64)
    private String timezone;

    @ManyToMany(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @JoinTable(name = "ai_agent_sip_trunk",
            joinColumns = @JoinColumn(name = "ai_agent_id"),
            inverseJoinColumns = @JoinColumn(name = "sip_trunk_id"))
    private Set<SipTrunkEntity> sipTrunks = new LinkedHashSet<>();

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

    public String getPronunciationRules() {
        return pronunciationRules;
    }

    public void setPronunciationRules(String pronunciationRules) {
        this.pronunciationRules = pronunciationRules;
    }

    public String getPostCallActions() {
        return postCallActions;
    }

    public void setPostCallActions(String postCallActions) {
        this.postCallActions = postCallActions;
    }

    public String getHandbook() {
        return handbook;
    }

    public void setHandbook(String handbook) {
        this.handbook = handbook;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Set<SipTrunkEntity> getSipTrunks() {
        return sipTrunks;
    }

    /** The ids of {@link #getSipTrunks()}, which is what the domain and the dialer carry. */
    public Set<Long> getSipTrunkIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (SipTrunkEntity trunk : sipTrunks) {
            ids.add(trunk.getId());
        }
        return ids;
    }

    /** In place, for the reason {@link #setLanguageVoices(Map)} gives. */
    public void setSipTrunks(Set<SipTrunkEntity> sipTrunks) {
        if (sipTrunks == this.sipTrunks) {
            return;
        }
        this.sipTrunks.clear();
        if (sipTrunks != null) {
            this.sipTrunks.addAll(sipTrunks);
        }
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
