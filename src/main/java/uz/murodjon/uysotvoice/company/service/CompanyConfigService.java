package uz.murodjon.uysotvoice.company.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.dto.UpdateCompanyConfigRequest;
import uz.murodjon.uysotvoice.company.enums.Language;
import uz.murodjon.uysotvoice.company.repository.CompanyConfigRepository;
import uz.murodjon.uysotvoice.shared.dialog.Disclosure;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
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

    private static final Language DEFAULT_LANGUAGE = Language.UZ_UZ;
    private static final List<Language> DEFAULT_LANGUAGES = List.of(DEFAULT_LANGUAGE);
    private static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(20, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";

    /**
     * The §11.1 disclosure every new company starts with — the platform's own wording,
     * with the tenant's name filled in at call time. Provisioned per company rather than
     * left blank so that a company owner can see the notice their calls open with, and
     * edit it, instead of it being an invisible constant in the code.
     */
    private static final String DEFAULT_DISCLOSURE_TEXT =
            "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.";

    private final CompanyConfigRepository repo;
    private final CompanyAccessGuard access;
    private final AuditService audit;

    public CompanyConfigService(CompanyConfigRepository repo, CompanyAccessGuard access, AuditService audit) {
        this.repo = repo;
        this.access = access;
        this.audit = audit;
    }

    /** Provisions {@code companyId}'s config row with sane defaults — called right after a company is created. */
    public void createDefault(long companyId) {
        repo.create(companyId, DEFAULT_WINDOW_START, DEFAULT_WINDOW_END, DEFAULT_TIMEZONE,
                DEFAULT_LANGUAGE, DEFAULT_LANGUAGES, DEFAULT_DISCLOSURE_TEXT);
    }

    /** {@code companyId}'s config, or {@code null} — the dialer's hot path; never throws. */
    public CompanyConfig find(long companyId) {
        return repo.find(companyId);
    }

    /** As {@link #find}, for the REST API — a missing config is a 404, not a null. */
    public CompanyConfig requireConfig(long companyId) {
        CompanyConfig row = repo.find(companyId);
        if (row == null) {
            throw new NotFoundException(ErrorCode.COMPANY_CONFIG_NOT_FOUND, companyId);
        }
        return row;
    }

    /**
     * As {@link #requireConfig}, but for {@code GET /api/companies/{id}/config} directly
     * (report #3) — {@link #requireConfig} itself stays unscoped since {@link
     * #resolveLanguage} calls it on the dialer's hot path with the caller's own,
     * already-legitimate company id, not a path parameter to police.
     */
    public CompanyConfig requireConfigForApi(long companyId) {
        access.requireOwnOrSuperadmin(companyId);
        return requireConfig(companyId);
    }

    public CompanyConfig update(long companyId, UpdateCompanyConfigRequest r) {
        access.requireOwnOrSuperadmin(companyId);
        requireConfig(companyId);
        if (!r.supportedLanguages().contains(r.defaultLanguage())) {
            throw new ValidationException(ErrorCode.COMPANY_CONFIG_DEFAULT_LANGUAGE_NOT_IN_SUPPORTED,
                    r.defaultLanguage().code(), codesOf(r.supportedLanguages()));
        }
        // §11.1 is the platform's obligation, not the tenant's choice: a company may word
        // the notice, or clear it and get the platform's wording, but it cannot replace it
        // with a text that discloses neither of the two things it has to.
        if (r.disclosureText() != null && !r.disclosureText().isBlank()
                && !Disclosure.discloses(r.disclosureText())) {
            throw new ValidationException(ErrorCode.COMPANY_CONFIG_DISCLOSURE_INCOMPLETE);
        }
        repo.update(companyId, r.dialWindowStart(), r.dialWindowEnd(), r.timezone(),
                r.defaultLanguage(), r.supportedLanguages(), r.disclosureText());
        audit.record("COMPANY_CONFIG_UPDATE", "company_config", String.valueOf(companyId),
                codesOf(r.supportedLanguages()).toString());
        return requireConfig(companyId);
    }

    /**
     * Resolves the language a campaign/inbound route should run with (ROADMAP B.1):
     * {@code requested} validated against {@code companyId}'s supported list, or the
     * company's explicit {@code defaultLanguage} when {@code requested} is null/blank
     * (backend-uchun-talablar.md §13 — previously the supported list's index 0).
     *
     * <p>Takes/returns a raw BCP-47 {@code String}, not {@link Language} — {@code
     * campaign}/{@code inbound} carry their own language field as a free {@code String}
     * (their own scope, not part of report #6's "company settings become a select"
     * ask), so this is where the closed-set check actually happens: an unparseable code
     * is rejected the same as one that parses but isn't in {@code supportedLanguages}.
     */
    public String resolveLanguage(long companyId, String requested) {
        CompanyConfig config = requireConfig(companyId);
        if (requested == null || requested.isBlank()) {
            return config.defaultLanguage().code();
        }
        Language language;
        try {
            language = Language.fromCode(requested);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.LANGUAGE_CODE_INVALID, e.getMessage());
        }
        if (!config.supportedLanguages().contains(language)) {
            throw new ValidationException(ErrorCode.COMPANY_CONFIG_LANGUAGE_NOT_SUPPORTED,
                    requested, codesOf(config.supportedLanguages()));
        }
        return language.code();
    }

    private static List<String> codesOf(List<Language> languages) {
        return languages.stream().map(Language::code).toList();
    }
}
