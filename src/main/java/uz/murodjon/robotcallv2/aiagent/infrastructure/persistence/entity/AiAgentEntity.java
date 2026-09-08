package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.aiagent.domain.enums.*;
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

    @Column(name = "preemptive_generation", nullable = false)
    private boolean preemptiveGeneration;

    @Column(name = "ivr_navigation_enabled", nullable = false)
    private boolean ivrNavigationEnabled = true;

    @Column(name = "use_rag", nullable = false)
    private boolean useRag;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_mode", nullable = false)
    private PipelineMode pipelineMode = PipelineMode.CASCADE;

    @Column(name = "realtime_provider")
    private String realtimeProvider;

    @Column(name = "pipecat_stt")
    private String pipecatStt;

    @Column(name = "pipecat_llm")
    private String pipecatLlm;

    @Column(name = "pipecat_tts")
    private String pipecatTts;

    @Column(name = "stt_provider")
    private String sttProvider;

    @Column(name = "stt_model")
    private String sttModel;

    @Column(name = "tts_provider")
    private String ttsProvider;

    @Column(name = "tts_model")
    private String ttsModel;

    @Column(name = "tts_voice")
    private String ttsVoice;

    @Column(name = "voice_speed")
    private Double voiceSpeed = 1.0;

    @Column(name = "voice_stability")
    private Double voiceStability = 0.5;

    @Column(name = "voice_similarity_boost")
    private Double voiceSimilarityBoost = 0.75;

    @ManyToMany(fetch = FetchType.EAGER)
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

    @Column(name = "zero_pii_retention", nullable = false)
    private boolean zeroPiiRetention;

    @Column(name = "store_call_audio", nullable = false)
    private boolean storeCallAudio = true;

    @Column(name = "conversation_retention_days")
    private Integer conversationRetentionDays = 30;

    @Column(name = "max_conversation_duration_seconds")
    private Integer maxConversationDurationSeconds;

    @Column(name = "silence_end_call_timeout_seconds")
    private Integer silenceEndCallTimeoutSeconds;

    @Column(name = "turn_timeout_seconds")
    private Integer turnTimeoutSeconds;

    @Column(name = "concurrent_calls_limit")
    private Integer concurrentCallsLimit;

    @Column(name = "daily_calls_limit")
    private Integer dailyCallsLimit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initiation_webhook", columnDefinition = "jsonb")
    private String initiationWebhook;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_call_webhook", columnDefinition = "jsonb")
    private String postCallWebhook;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambient_sound", nullable = false)
    private AmbientSound ambientSound = AmbientSound.OFFICE;

    @Column(name = "ambient_sound_volume", nullable = false)
    private double ambientSoundVolume = 1.0;

    @Column(name = "ambient_sound_fade_in_seconds", nullable = false)
    private double ambientSoundFadeInSeconds = 1.5;

    @Enumerated(EnumType.STRING)
    @Column(name = "thinking_sound", nullable = false, length = 50)
    private AmbientSound thinkingSound = AmbientSound.OFF;

    @Column(name = "thinking_sound_volume", nullable = false)
    private double thinkingSoundVolume = 1.0;

    @Column(name = "noise_cancellation_enabled", nullable = false)
    private boolean noiseCancellationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "noise_cancellation_mode", nullable = false, length = 50)
    private NoiseCancellationMode noiseCancellationMode = NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;

    @Column(name = "emotion_adaptive_voice", nullable = false)
    private boolean emotionAdaptiveVoice;

    @Column(name = "dtmf_input_enabled", nullable = false)
    private boolean dtmfInputEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "voicemail_action", nullable = false)
    private VoicemailAction voicemailAction = VoicemailAction.LEAVE_MESSAGE;

    @Column(name = "voicemail_message", columnDefinition = "TEXT")
    private String voicemailMessage;

    @Column(name = "mid_call_sms_enabled", nullable = false)
    private boolean midCallSmsEnabled;

    @Column(name = "mid_call_sms_template", columnDefinition = "TEXT")
    private String midCallSmsTemplate;

    @Column(name = "transfer_phone_number", length = 32)
    private String transferPhoneNumber;

    @Column(name = "transfer_message", columnDefinition = "TEXT")
    private String transferMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "interruption_sensitivity", nullable = false, length = 32)
    private InterruptionSensitivity interruptionSensitivity = InterruptionSensitivity.MEDIUM;

    @Column(name = "endpointing_delay_ms", nullable = false)
    private int endpointingDelayMs = 700;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pronunciation_rules", columnDefinition = "jsonb")
    private String pronunciationRules = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_call_actions", columnDefinition = "jsonb")
    private String postCallActions = "[]";

    @ManyToMany(fetch = FetchType.EAGER)
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

    public Long getId() {
        return id;
    }

    public double getAmbientSoundVolume() {
        return ambientSoundVolume;
    }

    public void setAmbientSoundVolume(double ambientSoundVolume) {
        this.ambientSoundVolume = ambientSoundVolume;
    }

    public double getAmbientSoundFadeInSeconds() {
        return ambientSoundFadeInSeconds;
    }

    public void setAmbientSoundFadeInSeconds(double ambientSoundFadeInSeconds) {
        this.ambientSoundFadeInSeconds = ambientSoundFadeInSeconds;
    }

    public AmbientSound getThinkingSound() {
        return thinkingSound;
    }

    public void setThinkingSound(AmbientSound thinkingSound) {
        this.thinkingSound = thinkingSound != null ? thinkingSound : AmbientSound.OFF;
    }

    public double getThinkingSoundVolume() {
        return thinkingSoundVolume;
    }

    public void setThinkingSoundVolume(double thinkingSoundVolume) {
        this.thinkingSoundVolume = thinkingSoundVolume;
    }

    public boolean isNoiseCancellationEnabled() {
        return noiseCancellationEnabled;
    }

    public void setNoiseCancellationEnabled(boolean noiseCancellationEnabled) {
        this.noiseCancellationEnabled = noiseCancellationEnabled;
    }

    public NoiseCancellationMode getNoiseCancellationMode() {
        return noiseCancellationMode;
    }

    public void setNoiseCancellationMode(NoiseCancellationMode noiseCancellationMode) {
        this.noiseCancellationMode = noiseCancellationMode != null ? noiseCancellationMode : NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;
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

    public boolean isPreemptiveGeneration() {
        return preemptiveGeneration;
    }

    public void setPreemptiveGeneration(boolean preemptiveGeneration) {
        this.preemptiveGeneration = preemptiveGeneration;
    }

    public boolean isIvrNavigationEnabled() {
        return ivrNavigationEnabled;
    }

    public void setIvrNavigationEnabled(boolean ivrNavigationEnabled) {
        this.ivrNavigationEnabled = ivrNavigationEnabled;
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

    public PipelineMode getPipelineMode() {
        return pipelineMode;
    }

    public void setPipelineMode(PipelineMode pipelineMode) {
        this.pipelineMode = pipelineMode != null ? pipelineMode : PipelineMode.CASCADE;
    }

    public String getRealtimeProvider() {
        return realtimeProvider;
    }

    public void setRealtimeProvider(String realtimeProvider) {
        this.realtimeProvider = realtimeProvider;
    }

    public String getPipecatStt() {
        return pipecatStt;
    }

    public void setPipecatStt(String pipecatStt) {
        this.pipecatStt = pipecatStt;
    }

    public String getPipecatLlm() {
        return pipecatLlm;
    }

    public void setPipecatLlm(String pipecatLlm) {
        this.pipecatLlm = pipecatLlm;
    }

    public String getPipecatTts() {
        return pipecatTts;
    }

    public void setPipecatTts(String pipecatTts) {
        this.pipecatTts = pipecatTts;
    }

    public String getSttProvider() {
        return sttProvider;
    }

    public void setSttProvider(String sttProvider) {
        this.sttProvider = sttProvider;
    }

    public String getSttModel() {
        return sttModel;
    }

    public void setSttModel(String sttModel) {
        this.sttModel = sttModel;
    }

    public String getTtsProvider() {
        return ttsProvider;
    }

    public void setTtsProvider(String ttsProvider) {
        this.ttsProvider = ttsProvider;
    }

    public String getTtsModel() {
        return ttsModel;
    }

    public void setTtsModel(String ttsModel) {
        this.ttsModel = ttsModel;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public Double getVoiceSpeed() {
        return voiceSpeed;
    }

    public void setVoiceSpeed(Double voiceSpeed) {
        this.voiceSpeed = voiceSpeed;
    }

    public Double getVoiceStability() {
        return voiceStability;
    }

    public void setVoiceStability(Double voiceStability) {
        this.voiceStability = voiceStability;
    }

    public Double getVoiceSimilarityBoost() {
        return voiceSimilarityBoost;
    }

    public void setVoiceSimilarityBoost(Double voiceSimilarityBoost) {
        this.voiceSimilarityBoost = voiceSimilarityBoost;
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

    public boolean isZeroPiiRetention() {
        return zeroPiiRetention;
    }

    public void setZeroPiiRetention(boolean zeroPiiRetention) {
        this.zeroPiiRetention = zeroPiiRetention;
    }

    public boolean isStoreCallAudio() {
        return storeCallAudio;
    }

    public void setStoreCallAudio(boolean storeCallAudio) {
        this.storeCallAudio = storeCallAudio;
    }

    public Integer getConversationRetentionDays() {
        return conversationRetentionDays;
    }

    public void setConversationRetentionDays(Integer conversationRetentionDays) {
        this.conversationRetentionDays = conversationRetentionDays;
    }

    public Integer getMaxConversationDurationSeconds() {
        return maxConversationDurationSeconds;
    }

    public void setMaxConversationDurationSeconds(Integer maxConversationDurationSeconds) {
        this.maxConversationDurationSeconds = maxConversationDurationSeconds;
    }

    public Integer getSilenceEndCallTimeoutSeconds() {
        return silenceEndCallTimeoutSeconds;
    }

    public void setSilenceEndCallTimeoutSeconds(Integer silenceEndCallTimeoutSeconds) {
        this.silenceEndCallTimeoutSeconds = silenceEndCallTimeoutSeconds;
    }

    public Integer getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    public void setTurnTimeoutSeconds(Integer turnTimeoutSeconds) {
        this.turnTimeoutSeconds = turnTimeoutSeconds;
    }

    public Integer getConcurrentCallsLimit() {
        return concurrentCallsLimit;
    }

    public void setConcurrentCallsLimit(Integer concurrentCallsLimit) {
        this.concurrentCallsLimit = concurrentCallsLimit;
    }

    public Integer getDailyCallsLimit() {
        return dailyCallsLimit;
    }

    public void setDailyCallsLimit(Integer dailyCallsLimit) {
        this.dailyCallsLimit = dailyCallsLimit;
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

    public AmbientSound getAmbientSound() {
        return ambientSound;
    }

    public void setAmbientSound(AmbientSound ambientSound) {
        this.ambientSound = ambientSound != null ? ambientSound : AmbientSound.OFFICE;
    }

    public boolean isEmotionAdaptiveVoice() {
        return emotionAdaptiveVoice;
    }

    public void setEmotionAdaptiveVoice(boolean emotionAdaptiveVoice) {
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
    }

    public boolean isDtmfInputEnabled() {
        return dtmfInputEnabled;
    }

    public void setDtmfInputEnabled(boolean dtmfInputEnabled) {
        this.dtmfInputEnabled = dtmfInputEnabled;
    }

    public VoicemailAction getVoicemailAction() {
        return voicemailAction;
    }

    public void setVoicemailAction(VoicemailAction voicemailAction) {
        this.voicemailAction = voicemailAction != null ? voicemailAction : VoicemailAction.LEAVE_MESSAGE;
    }

    public String getVoicemailMessage() {
        return voicemailMessage;
    }

    public void setVoicemailMessage(String voicemailMessage) {
        this.voicemailMessage = voicemailMessage;
    }

    public boolean isMidCallSmsEnabled() {
        return midCallSmsEnabled;
    }

    public void setMidCallSmsEnabled(boolean midCallSmsEnabled) {
        this.midCallSmsEnabled = midCallSmsEnabled;
    }

    public String getMidCallSmsTemplate() {
        return midCallSmsTemplate;
    }

    public void setMidCallSmsTemplate(String midCallSmsTemplate) {
        this.midCallSmsTemplate = midCallSmsTemplate;
    }

    public String getTransferPhoneNumber() {
        return transferPhoneNumber;
    }

    public void setTransferPhoneNumber(String transferPhoneNumber) {
        this.transferPhoneNumber = transferPhoneNumber;
    }

    public String getTransferMessage() {
        return transferMessage;
    }

    public void setTransferMessage(String transferMessage) {
        this.transferMessage = transferMessage;
    }

    public InterruptionSensitivity getInterruptionSensitivity() {
        return interruptionSensitivity;
    }

    public void setInterruptionSensitivity(InterruptionSensitivity interruptionSensitivity) {
        this.interruptionSensitivity = interruptionSensitivity != null ? interruptionSensitivity : InterruptionSensitivity.MEDIUM;
    }

    public int getEndpointingDelayMs() {
        return endpointingDelayMs;
    }

    public void setEndpointingDelayMs(int endpointingDelayMs) {
        this.endpointingDelayMs = endpointingDelayMs > 0 ? endpointingDelayMs : 700;
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
}
