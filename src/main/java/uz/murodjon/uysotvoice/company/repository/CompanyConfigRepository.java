package uz.murodjon.uysotvoice.company.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.entity.CompanyConfigEntity;
import uz.murodjon.uysotvoice.company.enums.Language;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * JPA-backed DAO for {@code company_config} (ROADMAP B.1/B.3). Like {@link
 * CompanyRepository}, not scoped by {@code CurrentCompany} — every method takes the
 * target company id explicitly, since a config always belongs to a specific company,
 * never "the current one" implicitly.
 */
@Repository
public class CompanyConfigRepository {

    private final CompanyConfigJpaRepository jpa;

    public CompanyConfigRepository(CompanyConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    public long create(long companyId, LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                       Language defaultLanguage, List<Language> supportedLanguages, String disclosureText) {
        CompanyConfigEntity entity = new CompanyConfigEntity();
        entity.setCompanyId(companyId);
        entity.setDialWindowStart(dialWindowStart);
        entity.setDialWindowEnd(dialWindowEnd);
        entity.setTimezone(timezone);
        entity.setDefaultLanguage(defaultLanguage);
        entity.setSupportedLanguages(supportedLanguages);
        entity.setDisclosureText(disclosureText);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /**
     * {@code companyId}'s config, or {@code null} if it has none — every company gets one
     * at creation time ({@code CompanyService.create}), so a {@code null} here means
     * either a pre-B.1 row never migrated, or a genuinely unknown company id.
     */
    public CompanyConfig find(long companyId) {
        return jpa.findByCompanyId(companyId).map(CompanyConfigRepository::toRow).orElse(null);
    }

    /** No-op if {@code companyId} has no config row yet. */
    public void update(long companyId, LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                       Language defaultLanguage, List<Language> supportedLanguages, String disclosureText) {
        jpa.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setDialWindowStart(dialWindowStart);
            entity.setDialWindowEnd(dialWindowEnd);
            entity.setTimezone(timezone);
            entity.setDefaultLanguage(defaultLanguage);
            entity.setSupportedLanguages(supportedLanguages);
            entity.setDisclosureText(disclosureText);
            jpa.save(entity);
        });
    }

    private static CompanyConfig toRow(CompanyConfigEntity e) {
        return new CompanyConfig(e.getId(), e.getCompanyId(), e.getDialWindowStart(), e.getDialWindowEnd(),
                e.getTimezone(), e.getDefaultLanguage(), List.copyOf(e.getSupportedLanguages()),
                e.getDisclosureText(), e.getCreatedAt());
    }
}
