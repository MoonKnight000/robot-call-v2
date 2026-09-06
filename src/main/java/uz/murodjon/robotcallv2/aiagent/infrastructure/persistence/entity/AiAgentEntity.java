package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

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

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "scenario_id", nullable = false)
    private long scenarioId;

    @Column(nullable = false)
    private String language;

    @Column(name = "tts_voice")
    private String ttsVoice;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_agent_language_voice", joinColumns = @JoinColumn(name = "ai_agent_id"))
    @MapKeyColumn(name = "language")
    @Column(name = "tts_voice", nullable = false)
    private Map<String, String> languageVoices = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentPersona persona = AgentPersona.AI_ASSISTANT;

    @Column(name = "llm_model")
    private String llmModel;

    private Double temperature;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambient_sound", nullable = false)
    private AmbientSound ambientSound = AmbientSound.OFF;

    @Column(name = "emotion_adaptive_voice", nullable = false)
    private boolean emotionAdaptiveVoice = true;

    @Column(name = "dtmf_input_enabled", nullable = false)
    private boolean dtmfInputEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "voicemail_action", nullable = false)
    private VoicemailAction voicemailAction = VoicemailAction.HANGUP;

    @Column(name = "voicemail_message")
    private String voicemailMessage;

    @Column(name = "mid_call_sms_enabled", nullable = false)
    private boolean midCallSmsEnabled;

    @Column(name = "mid_call_sms_template")
    private String midCallSmsTemplate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_agent_sip_trunk", joinColumns = @JoinColumn(name = "ai_agent_id"))
    @Column(name = "sip_trunk_id", nullable = false)
    private Set<Long> sipTrunkIds = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
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

    public long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public Map<String, String> getLanguageVoices() {
        return languageVoices;
    }

    public void setLanguageVoices(Map<String, String> languageVoices) {
        this.languageVoices = languageVoices == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(languageVoices);
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

    public AmbientSound getAmbientSound() {
        return ambientSound;
    }

    public void setAmbientSound(AmbientSound ambientSound) {
        this.ambientSound = ambientSound != null ? ambientSound : AmbientSound.OFF;
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
        this.voicemailAction = voicemailAction != null ? voicemailAction : VoicemailAction.HANGUP;
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

    public Set<Long> getSipTrunkIds() {
        return sipTrunkIds;
    }

    public void setSipTrunkIds(Set<Long> sipTrunkIds) {
        this.sipTrunkIds = (sipTrunkIds == null || sipTrunkIds.isEmpty())
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(sipTrunkIds);
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
