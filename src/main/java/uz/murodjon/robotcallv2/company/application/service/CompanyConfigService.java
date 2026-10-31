package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.TelephonyEngineResponse;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyConfigRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateTelephonyEngineRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CompanyAccessGuard;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

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
    public CompanyConfig find(long companyId) {
        return companyConfigRepository.find(companyId);
    }

    @Override
    public CompanyConfig requireConfig(long companyId) {
        CompanyConfig config = companyConfigRepository.find(companyId);
        if (config == null) {
            throw new NotFoundException(ErrorCode.COMPANY_CONFIG_NOT_FOUND, companyId);
        }
        return config;
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
        int reviewThreshold = request.scorecardReviewThreshold() != null
                ? request.scorecardReviewThreshold() : existing.scorecardReviewThreshold();

        companyConfigRepository.update(companyId, CompanyConfig.settings(
                request.dialWindowStart(), request.dialWindowEnd(), request.timezone(),
                maxAttempts, quietDays, respectPromise, humanLike, reviewThreshold,
                existing.telephonyEngine()));
        // Who allowed the company's agents to pass as a person is the one line in this
        // record a regulator would ask for, so it is written out whenever it changes.
        String persona = humanLike != existing.humanLikePersonaAllowed()
                ? " humanLikePersonaAllowed=" + humanLike : "";
        audit.record(companyId, "COMPANY_CONFIG_UPDATE", "company_config", String.valueOf(companyId),
                "contactPolicy=" + maxAttempts + "/7d,quiet=" + quietDays + "d,promise=" + respectPromise + persona);
        return requireConfig(companyId);
    }

    @Override
    public TelephonyEngineResponse findTelephonyEngine(long companyId) {
        return new TelephonyEngineResponse(requireConfig(companyId).telephonyEngine());
    }

    @Override
    public TelephonyEngineResponse updateTelephonyEngine(long companyId, UpdateTelephonyEngineRequest request) {
        CompanyConfig existing = requireConfig(companyId);
        // The engine decides which media stack the platform runs the company's calls on; the
        // company adds its own trunks, but the stack under them is the platform's choice alone.
        companyConfigRepository.update(companyId, CompanyConfig.settings(
                existing.dialWindowStart(), existing.dialWindowEnd(), existing.timezone(),
                existing.maxAttemptsPerPhonePerWeek(), existing.quietDaysAfterConversation(),
                existing.respectPromisedDate(), existing.humanLikePersonaAllowed(),
                existing.scorecardReviewThreshold(), request.telephonyEngine()));
        audit.record(companyId, "COMPANY_TELEPHONY_ENGINE_UPDATE", "company_config", String.valueOf(companyId),
                existing.telephonyEngine() + " -> " + request.telephonyEngine());
        return findTelephonyEngine(companyId);
    }
}
