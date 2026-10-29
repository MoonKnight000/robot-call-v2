package uz.murodjon.robotcallv2.company.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for company_config (ROADMAP B.1/B.3).
 */
@Entity
@Table(name = "company_config")
public class CompanyConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(name = "dial_window_start", nullable = false)
    private LocalTime dialWindowStart;

    @Column(name = "dial_window_end", nullable = false)
    private LocalTime dialWindowEnd;

    @Column(nullable = false)
    private String timezone;

    @Convert(converter = LanguageConverter.class)
    @Column(name = "default_language", nullable = false)
    private Language defaultLanguage;

    // Eager (the mapper reads it outside any transaction); SUBSELECT loads it for a whole
    // list of configs in one query instead of one per config.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "company_config_language", joinColumns = @JoinColumn(name = "company_config_id"))
    @OrderColumn(name = "ord")
    @Convert(converter = LanguageConverter.class)
    @Column(name = "language", nullable = false)
    private List<Language> supportedLanguages = new ArrayList<>();

    @Column(name = "disclosure_text", length = 500)
    private String disclosureText;

    @Column(name = "max_attempts_per_phone_per_week", nullable = false)
    private int maxAttemptsPerPhonePerWeek;

    @Column(name = "quiet_days_after_conversation", nullable = false)
    private int quietDaysAfterConversation;

    @Column(name = "respect_promised_date", nullable = false)
    private boolean respectPromisedDate;

    @Column(name = "human_like_persona_allowed", nullable = false)
    private boolean humanLikePersonaAllowed;

    /** Days nothing is dialled on: ISO dates, comma-separated (NEXT-PLAN C.4). */
    @Column(name = "holidays", nullable = false)
    private String holidays = "";

    @Column(name = "scorecard_review_threshold", nullable = false)
    private int scorecardReviewThreshold = CompanyConfig.DEFAULT_SCORECARD_REVIEW_THRESHOLD;

    @Enumerated(EnumType.STRING)
    @Column(name = "telephony_engine", nullable = false, length = 32)
    private TelephonyEngine telephonyEngine = TelephonyEngine.ASTERISK;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
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

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Language getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(Language defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public List<Language> getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(List<Language> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public String getDisclosureText() {
        return disclosureText;
    }

    public void setDisclosureText(String disclosureText) {
        this.disclosureText = disclosureText;
    }

    public int getMaxAttemptsPerPhonePerWeek() {
        return maxAttemptsPerPhonePerWeek;
    }

    public void setMaxAttemptsPerPhonePerWeek(int maxAttemptsPerPhonePerWeek) {
        this.maxAttemptsPerPhonePerWeek = maxAttemptsPerPhonePerWeek;
    }

    public int getQuietDaysAfterConversation() {
        return quietDaysAfterConversation;
    }

    public void setQuietDaysAfterConversation(int quietDaysAfterConversation) {
        this.quietDaysAfterConversation = quietDaysAfterConversation;
    }

    public boolean isRespectPromisedDate() {
        return respectPromisedDate;
    }

    public void setRespectPromisedDate(boolean respectPromisedDate) {
        this.respectPromisedDate = respectPromisedDate;
    }

    public boolean isHumanLikePersonaAllowed() {
        return humanLikePersonaAllowed;
    }

    public void setHumanLikePersonaAllowed(boolean humanLikePersonaAllowed) {
        this.humanLikePersonaAllowed = humanLikePersonaAllowed;
    }

    public String getHolidays() {
        return holidays;
    }

    public void setHolidays(String holidays) {
        this.holidays = holidays;
    }

    public int getScorecardReviewThreshold() {
        return scorecardReviewThreshold;
    }

    public void setScorecardReviewThreshold(int scorecardReviewThreshold) {
        this.scorecardReviewThreshold = scorecardReviewThreshold;
    }

    public TelephonyEngine getTelephonyEngine() {
        return telephonyEngine != null ? telephonyEngine : TelephonyEngine.ASTERISK;
    }

    public void setTelephonyEngine(TelephonyEngine telephonyEngine) {
        this.telephonyEngine = telephonyEngine != null ? telephonyEngine : TelephonyEngine.ASTERISK;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
