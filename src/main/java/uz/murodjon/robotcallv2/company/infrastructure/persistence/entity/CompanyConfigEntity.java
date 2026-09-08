package uz.murodjon.robotcallv2.company.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.converter.LanguageConverter;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_config_language", joinColumns = @JoinColumn(name = "company_config_id"))
    @OrderColumn(name = "ord")
    @Convert(converter = LanguageConverter.class)
    @Column(name = "language", nullable = false)
    private List<Language> supportedLanguages = new ArrayList<>();

    @Column(name = "disclosure_text", length = 500)
    private String disclosureText;

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

    /**
     * Replaces the contents, never the list itself — a collection table whose instance is
     * swapped is re-inserted whole, and the rows it already has collide with their own copies.
     */
    public void setSupportedLanguages(List<Language> supportedLanguages) {
        if (supportedLanguages == this.supportedLanguages) {
            return;
        }
        this.supportedLanguages.clear();
        if (supportedLanguages != null) {
            this.supportedLanguages.addAll(supportedLanguages);
        }
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
