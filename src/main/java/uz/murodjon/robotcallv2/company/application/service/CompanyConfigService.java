package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyConfigRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.company.domain.service.CompanyValidator;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CompanyAccessGuard;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalDate;
import java.util.List;

@Service
public class CompanyConfigService implements CompanyConfigUseCase {

    private final CompanyConfigRepository companyConfigRepository;
    private final CompanyAccessGuard companyAccessGuard;
    private final AuditUseCase audit;
    private final CurrentUser currentUser;

    public CompanyConfigService(CompanyConfigRepository companyConfigRepository, CompanyAccessGuard companyAccessGuard,
                                AuditUseCase audit, CurrentUser currentUser) {
        this.companyConfigRepository = companyConfigRepository;
        this.companyAccessGuard = companyAccessGuard;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Override
    public void createDefault(long companyId) {
        companyConfigRepository.create(companyId, CompanyConfig.defaults());
    }

    @Override
    public List<String> findSupportedLanguageCodes(long companyId) {
        CompanyConfig config = find(companyId);
        if (config == null || config.supportedLanguages() == null) {
            return List.of();
        }
        return config.supportedLanguages().stream().map(Language::code).toList();
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
        companyAccessGuard.requireOwnOrSuperadmin(callerCompanyId, companyId);
        return requireConfig(companyId);
    }

    @Override
    public CompanyConfig update(long callerCompanyId, long companyId, UpdateCompanyConfigRequest request) {
        companyAccessGuard.requireOwnOrSuperadmin(callerCompanyId, companyId);
        CompanyConfig existing = requireConfig(companyId);
        CompanyValidator.validateLanguages(request.defaultLanguage(), request.supportedLanguages());
        CompanyValidator.validateDisclosure(request.disclosureText());

        int maxAttempts = request.maxAttemptsPerPhonePerWeek() != null
                ? request.maxAttemptsPerPhonePerWeek() : existing.maxAttemptsPerPhonePerWeek();
        int quietDays = request.quietDaysAfterConversation() != null
                ? request.quietDaysAfterConversation() : existing.quietDaysAfterConversation();
        boolean respectPromise = request.respectPromisedDate() != null
                ? request.respectPromisedDate() : existing.respectPromisedDate();
        boolean humanLike = request.humanLikePersonaAllowed() != null
                ? request.humanLikePersonaAllowed() : existing.humanLikePersonaAllowed();
        // Letting a company's agents pass as a person is the platform's legal exposure, not
        // the company's own setting: its admin could otherwise switch the §11.1 disclosure off.
        if (humanLike != existing.humanLikePersonaAllowed() && !currentUser.hasPermission(Permission.PLATFORM_ADMIN)) {
            throw new ForbiddenException(ErrorCode.HUMAN_LIKE_PERSONA_PLATFORM_ONLY);
        }
        List<LocalDate> holidays = request.holidays() != null ? request.holidays() : existing.holidays();
        int reviewThreshold = request.scorecardReviewThreshold() != null
                ? request.scorecardReviewThreshold() : existing.scorecardReviewThreshold();
        TelephonyEngine telephonyEngine = request.telephonyEngine() != null
                ? request.telephonyEngine() : existing.telephonyEngine();
        // The engine decides which media stack the platform runs the company's calls on;
        // the company adds its own trunks, but the stack under them is the platform's choice.
        if (telephonyEngine != existing.telephonyEngine() && !currentUser.hasPermission(Permission.PLATFORM_ADMIN)) {
            throw new ForbiddenException(ErrorCode.TELEPHONY_ENGINE_PLATFORM_ONLY);
        }

        companyConfigRepository.update(companyId, CompanyConfig.settings(
                request.dialWindowStart(), request.dialWindowEnd(), request.timezone(),
                request.defaultLanguage(), request.supportedLanguages(), request.disclosureText(),
                maxAttempts, quietDays, respectPromise, humanLike, holidays, reviewThreshold, telephonyEngine));
        // Who allowed the company's agents to pass as a person is the one line in this
        // record a regulator would ask for, so it is written out whenever it changes.
        String persona = humanLike != existing.humanLikePersonaAllowed()
                ? " humanLikePersonaAllowed=" + humanLike : "";
        audit.record(companyId, "COMPANY_CONFIG_UPDATE", "company_config", String.valueOf(companyId),
                codesOf(request.supportedLanguages()) + " contactPolicy=" + maxAttempts + "/7d,quiet="
                        + quietDays + "d,promise=" + respectPromise + ",holidays=" + holidays.size() + persona
                        + " engine=" + telephonyEngine);
        return requireConfig(companyId);
    }

    @Override
    public String resolveLanguage(long companyId, String requested) {
        CompanyConfig config = requireConfig(companyId);
        if (requested == null || requested.isBlank()) {
            return config.defaultLanguage().code();
        }
        Language language = CompanyValidator.parseLanguage(requested);
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
