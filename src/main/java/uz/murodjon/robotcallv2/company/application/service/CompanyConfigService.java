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

import java.util.List;

@Service
public class CompanyConfigService implements CompanyConfigUseCase {

    private final CompanyConfigRepository companyConfigRepository;
    private final CompanyAccessGuard access;
    private final AuditService audit;

    public CompanyConfigService(CompanyConfigRepository companyConfigRepository, CompanyAccessGuard access,
                                AuditService audit) {
        this.companyConfigRepository = companyConfigRepository;
        this.access = access;
        this.audit = audit;
    }

    @Override
    public void createDefault(long companyId) {
        companyConfigRepository.create(companyId, CompanyConfig.defaults());
    }

    @Override
    public CompanyConfig find(long companyId) {
        return companyConfigRepository.find(companyId);
    }

    @Override
    public CompanyConfig requireConfig(long companyId) {
        CompanyConfig row = companyConfigRepository.find(companyId);
        if (row == null) {
            throw new NotFoundException(ErrorCode.COMPANY_CONFIG_NOT_FOUND, companyId);
        }
        return row;
    }

    @Override
    public CompanyConfig requireConfigForApi(long callerCompanyId, long companyId) {
        access.requireOwnOrSuperadmin(callerCompanyId, companyId);
        return requireConfig(companyId);
    }

    @Override
    public CompanyConfig update(long callerCompanyId, long companyId, UpdateCompanyConfigRequest request) {
        access.requireOwnOrSuperadmin(callerCompanyId, companyId);
        requireConfig(companyId);
        CompanyValidator.validateLanguages(request.defaultLanguage(), request.supportedLanguages());
        CompanyValidator.validateDisclosure(request.disclosureText());

        companyConfigRepository.update(companyId, CompanyConfig.settings(
                request.dialWindowStart(), request.dialWindowEnd(), request.timezone(),
                request.defaultLanguage(), request.supportedLanguages(), request.disclosureText()));
        audit.record(companyId, "COMPANY_CONFIG_UPDATE", "company_config", String.valueOf(companyId),
                codesOf(request.supportedLanguages()).toString());
        return requireConfig(companyId);
    }

    @Override
    public String resolveLanguage(long companyId, String requested) {
        CompanyConfig config = requireConfig(companyId);
        if (requested == null || requested.isBlank()) {
            return config.defaultLanguage().code();
        }
        Language language = Language.fromCode(requested);
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
