package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyConfigRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.company.domain.service.CompanyValidator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalTime;
import java.util.List;

@Service
public class CompanyConfigService implements CompanyConfigUseCase {

    private static final Language DEFAULT_LANGUAGE = Language.UZ_UZ;
    private static final List<Language> DEFAULT_LANGUAGES = List.of(DEFAULT_LANGUAGE);
    private static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(20, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";
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

    @Override
    public void createDefault(long companyId) {
        repo.create(companyId, DEFAULT_WINDOW_START, DEFAULT_WINDOW_END, DEFAULT_TIMEZONE,
                DEFAULT_LANGUAGE, DEFAULT_LANGUAGES, DEFAULT_DISCLOSURE_TEXT);
    }

    @Override
    public CompanyConfig find(long companyId) {
        return repo.find(companyId);
    }

    @Override
    public CompanyConfig requireConfig(long companyId) {
        CompanyConfig row = repo.find(companyId);
        if (row == null) {
            throw new NotFoundException(ErrorCode.COMPANY_CONFIG_NOT_FOUND, companyId);
        }
        return row;
    }

    @Override
    public CompanyConfig requireConfigForApi(long companyId) {
        access.requireOwnOrSuperadmin(companyId);
        return requireConfig(companyId);
    }

    @Override
    public CompanyConfig update(long companyId, UpdateCompanyConfigRequest r) {
        access.requireOwnOrSuperadmin(companyId);
        requireConfig(companyId);
        CompanyValidator.validateLanguages(r.defaultLanguage(), r.supportedLanguages());
        CompanyValidator.validateDisclosure(r.disclosureText());

        repo.update(companyId, r.dialWindowStart(), r.dialWindowEnd(), r.timezone(),
                r.defaultLanguage(), r.supportedLanguages(), r.disclosureText());
        audit.record("COMPANY_CONFIG_UPDATE", "company_config", String.valueOf(companyId),
                codesOf(r.supportedLanguages()).toString());
        return requireConfig(companyId);
    }

    @Override
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
