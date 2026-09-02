package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.campaign.domain.enums.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * JPA entity for campaign (PROJECT.md §6).
 */
@Entity
@Table(name = "campaign")
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    @Column(name = "goal_prompt", nullable = false)
    private String goalPrompt;

    @Column(name = "script_config", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String scriptConfig;

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @Column(name = "dial_window_start", nullable = false)
    private LocalTime dialWindowStart;

    @Column(name = "dial_window_end", nullable = false)
    private LocalTime dialWindowEnd;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "retry_interval_minutes", nullable = false)
    private int retryIntervalMinutes;

    @Column(name = "max_concurrent_calls", nullable = false)
    private int maxConcurrentCalls;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_dial_day", joinColumns = @JoinColumn(name = "campaign_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false)
    private Set<DayOfWeek> dialDays = EnumSet.noneOf(DayOfWeek.class);

    @Column(name = "tts_voice")
    private String ttsVoice;

    /**
     * Voice per call language, for a campaign that dials more than one — Uzbek targets
     * spoken by an Uzbek voice, Russian ones by a Russian voice. A language with no
     * entry here falls back to {@link #ttsVoice}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_language_voice", joinColumns = @JoinColumn(name = "campaign_id"))
    @MapKeyColumn(name = "language")
    @Column(name = "tts_voice", nullable = false)
    private Map<String, String> languageVoices = new LinkedHashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_sip_trunk", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "sip_trunk_id", nullable = false)
    private Set<Long> sipTrunkIds = new LinkedHashSet<>();

    @Column(name = "daily_call_cap", nullable = false)
    private int dailyCallCap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private ScenarioEntity scenario;

    @Column(name = "disclosure_enabled", nullable = false)
    private boolean disclosureEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false)
    private RecurrenceType recurrenceType = RecurrenceType.ONCE;

    @Column(name = "recurring_day_of_month")
    private Integer recurringDayOfMonth;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "auto_reset_targets", nullable = false)
    private boolean autoResetTargets;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambient_sound", nullable = false)
    private AmbientSound ambientSound = AmbientSound.OFF;

    @Column(name = "mid_call_sms_enabled", nullable = false)
    private boolean midCallSmsEnabled;

    @Column(name = "mid_call_sms_template")
    private String midCallSmsTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "voicemail_action", nullable = false)
    private VoicemailAction voicemailAction = VoicemailAction.HANGUP;

    @Column(name = "voicemail_message")
    private String voicemailMessage;

    @Column(name = "dtmf_input_enabled", nullable = false)
    private boolean dtmfInputEnabled;

    @Column(name = "emotion_adaptive_voice", nullable = false)
    private boolean emotionAdaptiveVoice = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CampaignType getType() {
        return type;
    }

    public void setType(CampaignType type) {
        this.type = type;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public String getGoalPrompt() {
        return goalPrompt;
    }

    public void setGoalPrompt(String goalPrompt) {
        this.goalPrompt = goalPrompt;
    }

    public String getScriptConfig() {
        return scriptConfig;
    }

    public void setScriptConfig(String scriptConfig) {
        this.scriptConfig = scriptConfig;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public LocalTime getDialWindowStart() {
        return dialWindowStart;
    }

    public void setDialWindowStart(LocalTime dialWindowStart) {
        this.dialWindowStart = dialWindowStart;
    }

    public LocalTime getDialWindowEnd() {
        return dialWindowEnd;
    }

    public void setDialWindowEnd(LocalTime dialWindowEnd) {
        this.dialWindowEnd = dialWindowEnd;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryIntervalMinutes() {
        return retryIntervalMinutes;
    }

    public void setRetryIntervalMinutes(int retryIntervalMinutes) {
        this.retryIntervalMinutes = retryIntervalMinutes;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
    }

    public Long getCreatedById() {
        return createdBy != null ? createdBy.getId() : null;
    }

    public Set<DayOfWeek> getDialDays() {
        return dialDays;
    }

    public void setDialDays(Set<DayOfWeek> dialDays) {
        this.dialDays = (dialDays == null || dialDays.isEmpty())
                ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(dialDays);
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

    public Set<Long> getSipTrunkIds() {
        return sipTrunkIds;
    }

    public void setSipTrunkIds(Set<Long> sipTrunkIds) {
        this.sipTrunkIds = (sipTrunkIds == null || sipTrunkIds.isEmpty())
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(sipTrunkIds);
    }

    public int getDailyCallCap() {
        return dailyCallCap;
    }

    public void setDailyCallCap(int dailyCallCap) {
        this.dailyCallCap = dailyCallCap;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
    }

    public ScenarioEntity getScenario() {
        return scenario;
    }

    public void setScenario(ScenarioEntity scenario) {
        this.scenario = scenario;
    }

    public long getScenarioId() {
        return scenario != null ? scenario.getId() : 0L;
    }

    public boolean isDisclosureEnabled() {
        return disclosureEnabled;
    }

    public void setDisclosureEnabled(boolean disclosureEnabled) {
        this.disclosureEnabled = disclosureEnabled;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(RecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
    }

    public Integer getRecurringDayOfMonth() {
        return recurringDayOfMonth;
    }

    public void setRecurringDayOfMonth(Integer recurringDayOfMonth) {
        this.recurringDayOfMonth = recurringDayOfMonth;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public boolean isAutoResetTargets() {
        return autoResetTargets;
    }

    public void setAutoResetTargets(boolean autoResetTargets) {
        this.autoResetTargets = autoResetTargets;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public AmbientSound getAmbientSound() {
        return ambientSound;
    }

    public void setAmbientSound(AmbientSound ambientSound) {
        this.ambientSound = ambientSound != null ? ambientSound : AmbientSound.OFF;
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

    public boolean isDtmfInputEnabled() {
        return dtmfInputEnabled;
    }

    public void setDtmfInputEnabled(boolean dtmfInputEnabled) {
        this.dtmfInputEnabled = dtmfInputEnabled;
    }

    public boolean isEmotionAdaptiveVoice() {
        return emotionAdaptiveVoice;
    }

    public void setEmotionAdaptiveVoice(boolean emotionAdaptiveVoice) {
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
    }
}
