package uz.murodjon.uysotvoice.company.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

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

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_config_language", joinColumns = @JoinColumn(name = "company_config_id"))
    @OrderColumn(name = "ord")
    @Column(name = "language", nullable = false)
    private List<String> supportedLanguages = new ArrayList<>();

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

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public List<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(List<String> supportedLanguages) {
        this.supportedLanguages = supportedLanguages != null ? supportedLanguages : new ArrayList<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
