package uz.murodjon.uysotvoice.company.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import uz.murodjon.uysotvoice.company.enums.Language;
import uz.murodjon.uysotvoice.company.enums.LanguageConverter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for {@code company_config} (ROADMAP B.1/B.3) — a company's operational
 * settings, one row per company. Split out of {@link CompanyEntity} itself (which stays
 * identity-only: name/status) because these are configuration a company owner edits,
 * not facts about who the company is.
 *
 * <p>{@code dialWindowStart}/{@code dialWindowEnd} are a <strong>strict</strong> ceiling:
 * {@code DialerService} checks them in addition to each campaign's own window — a
 * campaign can never dial outside its company's allowed hours, even if the campaign
 * row itself says otherwise.
 *
 * <p>{@code defaultLanguage} is used when a campaign/route is created without picking
 * one explicitly (backend-uchun-talablar.md §13 — previously only implied by {@code
 * supportedLanguages}' first entry). {@code supportedLanguages} lists every language a
 * campaign or inbound route is allowed to declare, {@code defaultLanguage} included —
 * see {@code CompanyConfigService#resolveLanguage}.
 */
@Entity
@Table(name = "company_config")
public class CompanyConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column(name = "dial_window_start", nullable = false)
    private LocalTime dialWindowStart;

    @Column(name = "dial_window_end", nullable = false)
    private LocalTime dialWindowEnd;

    @Column(nullable = false)
    private String timezone;

    @Convert(converter = LanguageConverter.class)
    @Column(name = "default_language", nullable = false)
    private Language defaultLanguage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_config_language", joinColumns = @JoinColumn(name = "company_config_id"))
    @OrderColumn(name = "ord")
    @Convert(converter = LanguageConverter.class)
    @Column(name = "language", nullable = false)
    private List<Language> supportedLanguages = new ArrayList<>();

    /**
     * This company's §11.1 opening disclosure, spoken at the start of every one of its
     * calls. Every company is provisioned with the platform's wording, so this is
     * normally set; blank falls back to {@code DialogPhrases.disclosure} in the call's
     * own language, and a scenario may still override it for its own flow.
     */
    @Column(name = "disclosure_text", length = 500)
    private String disclosureText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
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
        this.supportedLanguages = supportedLanguages != null ? supportedLanguages : new ArrayList<>();
    }

    public String getDisclosureText() {
        return disclosureText;
    }

    public void setDisclosureText(String disclosureText) {
        this.disclosureText = disclosureText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
