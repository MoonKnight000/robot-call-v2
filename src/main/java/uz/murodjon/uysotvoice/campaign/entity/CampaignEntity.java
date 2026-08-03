package uz.murodjon.uysotvoice.campaign.entity;

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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** JPA entity for {@code campaign} (PROJECT.md §6). */
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
    // TODO: extract into its own entity so a goal prompt can be reused as a template
    // across campaigns instead of being copied per-row.
    @Column(name = "goal_prompt", nullable = false)
    private String goalPrompt;

    @Column(name = "script_config", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String scriptConfig;

    /** BCP-47 code; validated at write time against the company's {@code supportedLanguages} —
     * see {@code CompanyConfigService#resolveLanguage}. Not a Java enum: the allowed set is
     * per-company config, not a fixed global list (docs/api/campaigns.md). */
    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @Column(name = "dial_window_start", nullable = false)
    private LocalTime dialWindowStart;

    @Column(name = "dial_window_end", nullable = false)
    private LocalTime dialWindowEnd;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "retry_interval_hours", nullable = false)
    private int retryIntervalHours;

    @Column(name = "max_concurrent_calls", nullable = false)
    private int maxConcurrentCalls;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    /** {@code app_user.id} who created this campaign; resolved to a name via {@code
     * UserService#namesByIds} at the API layer (see {@code CampaignRow#createdByName}) —
     * not a JPA relation, matching every other {@code *_id} column in this entity. */
    @Column(name = "created_by")
    private Long createdBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_dial_day", joinColumns = @JoinColumn(name = "campaign_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false)
    private Set<DayOfWeek> dialDays = EnumSet.noneOf(DayOfWeek.class);

    @Column(name = "tts_voice")
    private String ttsVoice;

    @Column(name = "daily_call_cap", nullable = false)
    private int dailyCallCap;
    /** Owning company; not a JPA relation on purpose — every feature scopes by raw
     * {@code companyId} via {@code CurrentCompany}, never a {@code @ManyToOne}. */
    @Column(name = "company_id", nullable = false)
    private long companyId;

    /** Fixed at creation (ROADMAP A.3); resolved to a name via {@code ScenarioService
     * #scenarioNamesByIds} at the API layer (see {@code CampaignRow#scenarioName}). */
    @Column(name = "scenario_id", nullable = false)
    private long scenarioId;

    @Column(name = "disclosure_enabled", nullable = false)
    private boolean disclosureEnabled;

    public Long getId() {
        return id;
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

    public int getRetryIntervalHours() {
        return retryIntervalHours;
    }

    public void setRetryIntervalHours(int retryIntervalHours) {
        this.retryIntervalHours = retryIntervalHours;
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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Set<DayOfWeek> getDialDays() {
        return dialDays;
    }

    public void setDialDays(Set<DayOfWeek> dialDays) {
        // EnumSet.copyOf throws on an empty non-EnumSet collection (it can't infer the
        // enum type from zero elements), so the empty case needs its own branch.
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

    public int getDailyCallCap() {
        return dailyCallCap;
    }

    public void setDailyCallCap(int dailyCallCap) {
        this.dailyCallCap = dailyCallCap;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public boolean isDisclosureEnabled() {
        return disclosureEnabled;
    }

    public void setDisclosureEnabled(boolean disclosureEnabled) {
        this.disclosureEnabled = disclosureEnabled;
    }
}
