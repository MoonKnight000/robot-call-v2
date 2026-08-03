package uz.murodjon.uysotvoice.company.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyConfigRequest;
import uz.murodjon.uysotvoice.company.repository.CompanyConfigRepository;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.time.LocalTime;
import java.util.List;

/**
 * Company settings (ROADMAP B.1/B.3): the strict dial window {@code DialerService}
 * enforces on top of every campaign's own window, and the supported-language list
 * {@code CampaignService}/{@code InboundRouteService} validate a chosen language
 * against. {@link #find} is the runtime hot path the dialer calls once per active
 * campaign per tick — it must never throw, only the admin CRUD methods below it do.
 */
@Service
public class CompanyConfigService {

    private static final String DEFAULT_LANGUAGE = "uz-UZ";
    private static final List<String> DEFAULT_LANGUAGES = List.of(DEFAULT_LANGUAGE);
    private static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(20, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";

    private final CompanyConfigRepository repo;
    private final AuditService audit;

    public CompanyConfigService(CompanyConfigRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** Provisions {@code companyId}'s config row with sane defaults — called right after a company is created. */
    public void createDefault(long companyId) {
        repo.create(companyId, DEFAULT_WINDOW_START, DEFAULT_WINDOW_END, DEFAULT_TIMEZONE,
                DEFAULT_LANGUAGE, DEFAULT_LANGUAGES);
    }

    /** {@code companyId}'s config, or {@code null} — the dialer's hot path; never throws. */
    public CompanyConfig find(long companyId) {
        return repo.find(companyId);
    }

    /** As {@link #find}, for the REST API — a missing config is a 404, not a null. */
    public CompanyConfig requireConfig(long companyId) {
        CompanyConfig row = repo.find(companyId);
        if (row == null) {
            throw new NotFoundException("company_config", companyId);
        }
        return row;
    }

    public CompanyConfig update(long companyId, UpdateCompanyConfigRequest r) {
        requireConfig(companyId);
        if (!r.supportedLanguages().contains(r.defaultLanguage())) {
            throw new ValidationException("defaultLanguage '" + r.defaultLanguage()
                    + "' must be one of supportedLanguages " + r.supportedLanguages());
        }
        repo.update(companyId, r.dialWindowStart(), r.dialWindowEnd(), r.timezone(),
                r.defaultLanguage(), r.supportedLanguages());
        audit.record("COMPANY_CONFIG_UPDATE", "company_config", String.valueOf(companyId),
                r.supportedLanguages().toString());
        return requireConfig(companyId);
    }

    /**
     * Resolves the language a campaign/inbound route should run with (ROADMAP B.1):
     * {@code requested} validated against {@code companyId}'s supported list, or the
     * company's explicit {@code defaultLanguage} when {@code requested} is null/blank
     * (backend-uchun-talablar.md §13 — previously the supported list's index 0).
     */
    public String resolveLanguage(long companyId, String requested) {
        CompanyConfig config = requireConfig(companyId);
        if (requested == null || requested.isBlank()) {
            return config.defaultLanguage();
        }
        if (!config.supportedLanguages().contains(requested)) {
            throw new ValidationException("Language '" + requested + "' is not supported by this company; "
                    + "supported: " + config.supportedLanguages());
        }
        return requested;
    }
}
