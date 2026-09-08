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
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final SessionService sessions;
    private final PasswordResetMailSender resetMail;
    private final RoleUseCase roleUseCase;

    public AuthService(UserRepository users, CompanyRepository companies,
                       PasswordEncoder passwordEncoder, JwtTokenService tokens, CurrentUser currentUser,
                       AuditService audit, SessionService sessions, PasswordResetMailSender resetMail,
                       RoleUseCase roleUseCase) {
        this.users = users;
        this.companies = companies;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.audit = audit;
        this.sessions = sessions;
        this.resetMail = resetMail;
        this.roleUseCase = roleUseCase;
    }

    @Override
    public List<Company> findMyCompanies(long companyId) {
        Company company = companies.find(companyId);
        return company == null ? List.of() : List.of(company);
    }

    @Override
    public LoginResponse loginWithUysotCallback() {
        throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_LOGIN_NOT_AVAILABLE, "uysot-oauth");
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username())
                .orElseThrow(() -> new ValidationException(ErrorCode.LOGIN_INVALID_CREDENTIALS));
        if (user.status() == UserStatus.BLOCKED) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (user.status() == UserStatus.INVITED || user.passwordHash() == null) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ValidationException(ErrorCode.LOGIN_INVALID_CREDENTIALS);
        }
        users.touchLastLogin(user.id());
        audit.record(user.companyId(), "USER_LOGIN", "user", String.valueOf(user.id()), user.email());
        IssuedSession issued = issueTokens(user);
        sessions.create(user.companyId(), user.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest request) {
        String tokenHash = Tokens.hash(request.refreshToken());
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

    @Override
    public void logout() {
        currentUser.id().ifPresent(sessions::revokeAllForUser);
    }

    @Override
    public LoginResponse activate(ActivateRequest request) {
        String tokenHash = Tokens.hash(request.token());
        User user = users.findByInviteTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException(ErrorCode.ACTIVATION_TOKEN_INVALID));
        if (user.inviteExpiresAt() == null || user.inviteExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException(ErrorCode.ACTIVATION_TOKEN_EXPIRED);
        }
        users.activate(user.id(), passwordEncoder.encode(request.password()));
        audit.record(user.companyId(), "USER_ACTIVATE", "user", String.valueOf(user.id()), user.email());
        User activated = users.findByEmail(user.email()).orElseThrow();
        IssuedSession issued = issueTokens(activated);
        sessions.create(activated.companyId(), activated.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        users.findByEmail(request.email())
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String token = Tokens.generate();
                    users.setResetToken(user.id(), Tokens.hash(token), Instant.now().plus(RESET_TTL));
                    audit.record(user.companyId(), "PASSWORD_RESET_REQUEST", "user", String.valueOf(user.id()), user.email());
                    try {
                        resetMail.send(user.email(), token);
                    } catch (Exception e) {
                        log.warn("Password reset email to {} failed: {}", user.email(), e.getMessage());
                    }
                });
    }

    @Override
    public LoginResponse resetPassword(ResetPasswordRequest request) {
        String tokenHash = Tokens.hash(request.token());
        User user = users.findByResetTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException(ErrorCode.RESET_TOKEN_INVALID));
        if (user.resetExpiresAt() == null || user.resetExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException(ErrorCode.RESET_TOKEN_EXPIRED);
        }
        users.resetPassword(user.id(), passwordEncoder.encode(request.newPassword()));
        audit.record(user.companyId(), "PASSWORD_RESET", "user", String.valueOf(user.id()), user.email());
        User updated = users.findByEmail(user.email()).orElseThrow();
        IssuedSession issued = issueTokens(updated);
        sessions.create(updated.companyId(), updated.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public CurrentUserResponse me(long companyId) {
        long userId = currentUser.id()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
        User user = users.find(companyId, userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, userId);
        }
        var company = companies.find(user.companyId());
        return new CurrentUserResponse(user.id(), user.name(), user.username(), user.email(),
                user.roleId(), user.roleCode(), user.roleName(), resolvePermissions(user),
                user.companyId(), company == null ? null : company.name());
    }

    private IssuedSession issueTokens(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.companyId(), user.roleId(),
                user.roleCode(), resolvePermissions(user), user.name(), user.email());
        IssuedToken issued = tokens.issue(principal);

        String refreshToken = Tokens.generate();
        Instant refreshExpiresAt = Instant.now().plus(REFRESH_TTL);

        LoginResponse response = new LoginResponse(issued.token(), issued.expiresAt(), refreshToken,
                refreshExpiresAt, UserRow.of(user));
        return new IssuedSession(response, Tokens.hash(refreshToken), refreshExpiresAt);
    }

    /** Everything the role grants right now — what the new token will carry. */
    private Set<Permission> resolvePermissions(User user) {
        Role role = roleUseCase.findRole(user.companyId(), user.roleId());
        return role == null ? Set.of() : role.permissions();
    }

    private record IssuedSession(LoginResponse response, String refreshTokenHash, Instant refreshExpiresAt) {
    }
}
