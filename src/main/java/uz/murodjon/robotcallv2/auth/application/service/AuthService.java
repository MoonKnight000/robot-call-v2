package uz.murodjon.robotcallv2.auth.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.auth.application.port.input.AuthUseCase;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final CompanyRepository companies;
    private final CurrentCompany currentCompany;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final SessionService sessions;
    private final PasswordResetMailSender resetMail;

    public AuthService(UserRepository users, CompanyRepository companies, CurrentCompany currentCompany,
                       PasswordEncoder passwordEncoder, JwtTokenService tokens, CurrentUser currentUser,
                       AuditService audit, SessionService sessions, PasswordResetMailSender resetMail) {
        this.users = users;
        this.companies = companies;
        this.currentCompany = currentCompany;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.audit = audit;
        this.sessions = sessions;
        this.resetMail = resetMail;
    }

    public List<Company> myCompanies() {
        Company company = companies.find(currentCompany.id());
        return company == null ? List.of() : List.of(company);
    }

    @Override
    public LoginResponse login(LoginRequest r, String device, String ipAddress) {
        User user = users.findByUsername(r.username())
                .orElseThrow(() -> new ValidationException(ErrorCode.LOGIN_INVALID_CREDENTIALS));
        if (user.status() == UserStatus.BLOCKED) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (user.status() == UserStatus.INVITED || user.passwordHash() == null) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }
        if (!passwordEncoder.matches(r.password(), user.passwordHash())) {
            throw new ValidationException(ErrorCode.LOGIN_INVALID_CREDENTIALS);
        }
        users.touchLastLogin(user.id());
        audit.record("USER_LOGIN", "user", String.valueOf(user.id()), user.email());
        IssuedSession issued = issueTokens(user);
        sessions.create(user.companyId(), user.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    public LoginResponse login(LoginRequest r) {
        return login(r, null, null);
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest r, String device, String ipAddress) {
        String tokenHash = Tokens.hash(r.refreshToken());
        UserSession session = sessions.findActiveByHash(tokenHash)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (session.expiresAt().isBefore(Instant.now())) {
            throw new ForbiddenException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        User user = users.findById(session.userId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (user.status() != UserStatus.ACTIVE) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_INACTIVE);
        }
        IssuedSession issued = issueTokens(user);
        sessions.rotate(session.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    public LoginResponse refresh(RefreshTokenRequest r) {
        return refresh(r, null, null);
    }

    public void logout() {
        currentUser.id().ifPresent(sessions::revokeAllForUser);
    }

    @Override
    public LoginResponse activate(ActivateRequest r) {
        String tokenHash = Tokens.hash(r.token());
        User user = users.findByInviteTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException(ErrorCode.ACTIVATION_TOKEN_INVALID));
        if (user.inviteExpiresAt() == null || user.inviteExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException(ErrorCode.ACTIVATION_TOKEN_EXPIRED);
        }
        users.activate(user.id(), passwordEncoder.encode(r.password()));
        audit.record("USER_ACTIVATE", "user", String.valueOf(user.id()), user.email());
        User activated = users.findByEmail(user.email()).orElseThrow();
        IssuedSession issued = issueTokens(activated);
        sessions.create(activated.companyId(), activated.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest r) {
        users.findByEmail(r.email())
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String token = Tokens.generate();
                    users.setResetToken(user.id(), Tokens.hash(token), Instant.now().plus(RESET_TTL));
                    audit.record("PASSWORD_RESET_REQUEST", "user", String.valueOf(user.id()), user.email());
                    try {
                        resetMail.send(user.email(), token);
                    } catch (Exception e) {
                        log.warn("Password reset email to {} failed: {}", user.email(), e.getMessage());
                    }
                });
    }

    @Override
    public LoginResponse resetPassword(ResetPasswordRequest r) {
        String tokenHash = Tokens.hash(r.token());
        User user = users.findByResetTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException(ErrorCode.RESET_TOKEN_INVALID));
        if (user.resetExpiresAt() == null || user.resetExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException(ErrorCode.RESET_TOKEN_EXPIRED);
        }
        users.resetPassword(user.id(), passwordEncoder.encode(r.newPassword()));
        audit.record("PASSWORD_RESET", "user", String.valueOf(user.id()), user.email());
        User updated = users.findByEmail(user.email()).orElseThrow();
        IssuedSession issued = issueTokens(updated);
        sessions.create(updated.companyId(), updated.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public CurrentUserResponse me() {
        long userId = currentUser.id()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
        User user = users.find(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, userId);
        }
        var company = companies.find(user.companyId());
        return new CurrentUserResponse(user.id(), user.name(), user.username(), user.email(), user.role(),
                user.companyId(), company == null ? null : company.name());
    }

    private IssuedSession issueTokens(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.companyId(), user.role(),
                user.name(), user.email());
        IssuedToken issued = tokens.issue(principal);

        String refreshToken = Tokens.generate();
        Instant refreshExpiresAt = Instant.now().plus(REFRESH_TTL);

        LoginResponse response = new LoginResponse(issued.token(), issued.expiresAt(), refreshToken,
                refreshExpiresAt, UserRow.of(user));
        return new IssuedSession(response, Tokens.hash(refreshToken), refreshExpiresAt);
    }

    private record IssuedSession(LoginResponse response, String refreshTokenHash, Instant refreshExpiresAt) {
    }
}
