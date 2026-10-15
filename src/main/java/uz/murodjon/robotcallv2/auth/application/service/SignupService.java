package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.auth.application.dto.SignupRequest;
import uz.murodjon.robotcallv2.auth.application.dto.SignupResponse;
import uz.murodjon.robotcallv2.auth.application.port.input.SignupUseCase;
import uz.murodjon.robotcallv2.auth.application.port.output.SignupMailSenderPort;
import uz.murodjon.robotcallv2.auth.infrastructure.config.SignupProperties;
import uz.murodjon.robotcallv2.billing.application.port.input.TrialCreditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

/**
 * Self-service signup: a company, its first administrator (invited, so the password is set
 * from the mailed link) and its free trial money — all in one transaction, so a mail that
 * cannot be sent leaves nothing behind and the same email can simply try again.
 */
@Service
public class SignupService implements SignupUseCase {

    private final CompanyUseCase companyUseCase;
    private final UserUseCase userUseCase;
    private final TrialCreditUseCase trialCreditUseCase;
    private final SignupMailSenderPort signupMailSenderPort;
    private final SignupProperties signupProperties;

    public SignupService(CompanyUseCase companyUseCase, UserUseCase userUseCase,
                         TrialCreditUseCase trialCreditUseCase, SignupMailSenderPort signupMailSenderPort,
                         SignupProperties signupProperties) {
        this.companyUseCase = companyUseCase;
        this.userUseCase = userUseCase;
        this.trialCreditUseCase = trialCreditUseCase;
        this.signupMailSenderPort = signupMailSenderPort;
        this.signupProperties = signupProperties;
    }

    @Override
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!signupProperties.enabled()) {
            throw new ForbiddenException(ErrorCode.SIGNUP_DISABLED);
        }
        // One email, one trial: checked before a company is created, so a repeated signup
        // is refused without leaving an empty tenant behind.
        if (userUseCase.existsByEmail(request.email())) {
            throw new ConflictException(ErrorCode.USER_EMAIL_TAKEN, request.email());
        }
        if (userUseCase.findByUsername(request.username()).isPresent()) {
            throw new ConflictException(ErrorCode.USER_USERNAME_TAKEN, request.username());
        }

        Company company = companyUseCase.create(new CreateCompanyRequest(request.companyName()));
        InviteUserResponse invite = userUseCase.inviteFirstAdmin(
                company.id(), request.name(), request.username(), request.email());
        trialCreditUseCase.grantTrialCredit(company.id());
        signupMailSenderPort.sendActivation(invite.email(), request.companyName(), invite.inviteToken());
        return new SignupResponse(company.id(), invite.email(), invite.expiresAt());
    }
}
