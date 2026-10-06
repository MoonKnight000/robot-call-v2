package uz.murodjon.robotcallv2.auth.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.auth.application.dto.*;
import uz.murodjon.robotcallv2.auth.application.port.input.AuthUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.TwoFactorUseCase;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.security.RateLimitProperties;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    /**
     * Failed logins are counted per username <em>and</em> client address. Keyed by username
     * alone, anyone who knew a username could keep its owner locked out from anywhere; a
     * guess spread over many addresses is slowed by the per-address RateLimitFilter instead.
     */
    private static final String LOGIN_FAILURES_KEY = "auth:login-fail:";

    private final UserUseCase userUseCase;
    private final CompanyUseCase companyUseCase;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final CurrentUser currentUser;
    private final AuditUseCase audit;
    private final SessionService sessionService;
    private final PasswordResetMailSender resetMail;
    private final RoleUseCase roleUseCase;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final TwoFactorUseCase twoFactor;
    private final StreamTicketService streamTicketService;
    private final Clock clock;
    /** See {@link #obtainDummyPasswordHash()}. */
    private volatile String dummyPasswordHash;

    public AuthService(UserUseCase userUseCase, CompanyUseCase companyUseCase,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService, CurrentUser currentUser,
                       AuditUseCase audit, SessionService sessionService, PasswordResetMailSender resetMail,
                       RoleUseCase roleUseCase, RateLimiter rateLimiter, RateLimitProperties rateLimitProperties,
                       TwoFactorUseCase twoFactor, StreamTicketService streamTicketService, Clock clock) {
        this.twoFactor = twoFactor;
        this.streamTicketService = streamTicketService;
        this.userUseCase = userUseCase;
        this.companyUseCase = companyUseCase;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.currentUser = currentUser;
        this.audit = audit;
        this.sessionService = sessionService;
        this.resetMail = resetMail;
        this.roleUseCase = roleUseCase;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.clock = clock;
    }

    @Override
    public List<Company> findMyCompanies(long companyId) {
        Company company = companyUseCase.findById(companyId);
        return company == null ? List.of() : List.of(company);
    }

    @Override
    public LoginResponse loginWithUysotCallback() {
        throw new ExternalServiceException(ErrorCode.UYSOT_OAUTH_LOGIN_NOT_AVAILABLE, "uysot-oauth");
    }

    /**
     * One answer for every way a login can fail — unknown username, wrong password,
     * blocked or not yet activated account — so the response never says whether the
     * account exists. The real reason goes to the audit trail, and every failure counts
     * toward the lock of this username from {@code clientAddress}.
     */
    @Override
    public LoginResponse login(LoginRequest request, String clientAddress) {
        String failuresKey = LOGIN_FAILURES_KEY + request.username().trim().toLowerCase() + ":" + clientAddress;
        int lockAfter = rateLimitProperties.loginFailuresPerUser();
        int lockoutMinutes = rateLimitProperties.lockoutMinutes();
        if (lockAfter > 0 && rateLimiter.count(failuresKey) >= lockAfter) {
            throw new TooManyRequestsException(ErrorCode.LOGIN_TEMPORARILY_LOCKED, lockoutMinutes);
        }
        User user = userUseCase.findByUsername(request.username()).orElse(null);
        String failure = loginFailure(user, request.password());
        if (failure != null) {
            long failures = rateLimiter.hit(failuresKey, Duration.ofMinutes(lockoutMinutes));
            if (user != null) {
                audit.record(user.companyId(), "LOGIN_FAILED", "user", String.valueOf(user.id()), failure);
                if (lockAfter > 0 && failures == lockAfter) {
                    audit.record(user.companyId(), "LOGIN_LOCKED", "user", String.valueOf(user.id()),
                            lockoutMinutes + " min, from " + clientAddress);
                }
            }
            throw new ValidationException(ErrorCode.LOGIN_INVALID_CREDENTIALS);
        }
        if (twoFactor.isEnabled(user.id())) {
            if (request.otpCode() == null || request.otpCode().isBlank()) {
                // The password was right; the second factor is what is missing. Not a failure.
                throw new ValidationException(ErrorCode.TOTP_REQUIRED);
            }
            if (!twoFactor.verifyCode(user.id(), request.otpCode())) {
                rateLimiter.hit(failuresKey, Duration.ofMinutes(lockoutMinutes));
                audit.record(user.companyId(), "LOGIN_FAILED", "user", String.valueOf(user.id()), "BAD_TOTP");
                throw new ValidationException(ErrorCode.TOTP_CODE_INVALID);
            }
        }
        requireCompanyActive(user.companyId());
        rateLimiter.clear(failuresKey);
        userUseCase.touchLastLogin(user.id());
        audit.record(user.companyId(), "USER_LOGIN", "user", String.valueOf(user.id()), user.email());
        IssuedSession issued = issueTokens(user);
        sessionService.create(user.companyId(), user.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    /** Why this login must be refused — for the audit trail only — or null when it may proceed. */
    private String loginFailure(User user, String password) {
        // One bcrypt check on every path, against a throwaway hash when there is no account
        // or no password yet: skipping it made those answers ~250 ms faster than a wrong
        // password, and the timing alone told an attacker which usernames exist.
        boolean passwordMatches = passwordEncoder.matches(password,
                user != null && user.passwordHash() != null ? user.passwordHash() : obtainDummyPasswordHash());
        if (user == null) {
            return "UNKNOWN_USERNAME";
        }
        if (user.status() == UserStatus.BLOCKED) {
            return ErrorCode.ACCOUNT_BLOCKED.name();
        }
        if (user.status() == UserStatus.INVITED || user.passwordHash() == null) {
            return ErrorCode.ACCOUNT_NOT_ACTIVATED.name();
        }
        if (!passwordMatches) {
            return "BAD_PASSWORD";
        }
        return null;
    }

    /** A real bcrypt hash of a password nobody knows, made on first use so start-up stays fast. */
    private String obtainDummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(Tokens.generate());
            dummyPasswordHash = hash;
        }
        return hash;
    }

    /**
     * Checked only once the password (and second factor) is proven, so the answer says
     * nothing about the account to someone who does not already hold it.
     */
    private void requireCompanyActive(long companyId) {
        Company company = companyUseCase.findById(companyId);
        if (company != null && company.status() == CompanyStatus.SUSPENDED) {
            throw new ForbiddenException(ErrorCode.COMPANY_SUSPENDED);
        }
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest request, String cookieRefreshToken) {
        String tokenHash = Tokens.hash(chooseRefreshToken(request, cookieRefreshToken));
        UserSession session = sessionService.findActiveByHash(tokenHash)
                .orElseThrow(() -> refreshRejected(tokenHash));
        if (session.expiresAt().isBefore(clock.instant())) {
            throw new ForbiddenException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        User user = userUseCase.findById(session.userId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (user.status() != UserStatus.ACTIVE) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_INACTIVE);
        }
        requireCompanyActive(user.companyId());
        IssuedSession issued = issueTokens(user);
        sessionService.rotate(session.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    /**
     * The body's token when a client still sends one (the older contract), otherwise the
     * {@code refresh_token} cookie.
     */
    private static String chooseRefreshToken(RefreshTokenRequest request, String cookieRefreshToken) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        if (cookieRefreshToken == null || cookieRefreshToken.isBlank()) {
            throw new ValidationException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        return cookieRefreshToken;
    }

    /**
     * A refresh token that is not active any more but <em>was</em> — rotated away by an
     * earlier refresh — has been presented twice, which only happens when two parties hold
     * it. Whichever one is the thief, the session is ended for both.
     */
    private ForbiddenException refreshRejected(String tokenHash) {
        return sessionService.findByPreviousHash(tokenHash)
                .map(stale -> {
                    sessionService.revokeAllForUser(stale.userId());
                    audit.record(stale.companyId(), "REFRESH_REUSE_DETECTED", "session",
                            String.valueOf(stale.id()), String.valueOf(stale.userId()));
                    return new ForbiddenException(ErrorCode.REFRESH_TOKEN_REUSED);
                })
                .orElseGet(() -> new ForbiddenException(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    @Override
    public void logout() {
        currentUser.id().ifPresent(sessionService::revokeAllForUser);
    }

    @Override
    public LoginResponse activate(ActivateRequest request) {
        String tokenHash = Tokens.hash(request.token());
        User user = userUseCase.findByInviteTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException(ErrorCode.ACTIVATION_TOKEN_INVALID));
        // Only an invitation still waiting to be accepted: activating set any status to
        // ACTIVE, so a blocked user whose invite link survived could unblock themselves.
        if (user.status() != UserStatus.INVITED) {
            throw new ValidationException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        }
        if (user.inviteExpiresAt() == null || user.inviteExpiresAt().isBefore(clock.instant())) {
            throw new ValidationException(ErrorCode.ACTIVATION_TOKEN_EXPIRED);
        }
        userUseCase.activate(user.id(), passwordEncoder.encode(request.password()));
        audit.record(user.companyId(), "USER_ACTIVATE", "user", String.valueOf(user.id()), user.email());
        User activated = userUseCase.findByEmail(user.email()).orElseThrow();
        IssuedSession issued = issueTokens(activated);
        sessionService.create(activated.companyId(), activated.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userUseCase.findByEmail(request.email())
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String token = Tokens.generate();
                    userUseCase.setResetToken(user.id(), Tokens.hash(token), clock.instant().plus(RESET_TTL));
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
        User user = userUseCase.findByResetTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException(ErrorCode.RESET_TOKEN_INVALID));
        // A reset ends in a fresh session, so it is only for someone who may log in at all.
        if (user.status() != UserStatus.ACTIVE) {
            throw new ValidationException(ErrorCode.RESET_TOKEN_INVALID);
        }
        if (user.resetExpiresAt() == null || user.resetExpiresAt().isBefore(clock.instant())) {
            throw new ValidationException(ErrorCode.RESET_TOKEN_EXPIRED);
        }
        // With two-factor login on, a reset link alone must not be a way in (NEXT-PLAN S.3).
        if (twoFactor.isEnabled(user.id()) && !twoFactor.verifyCode(user.id(), request.otpCode())) {
            throw new ValidationException(request.otpCode() == null || request.otpCode().isBlank()
                    ? ErrorCode.TOTP_REQUIRED : ErrorCode.TOTP_CODE_INVALID);
        }
        userUseCase.resetPassword(user.id(), passwordEncoder.encode(request.newPassword()));
        audit.record(user.companyId(), "PASSWORD_RESET", "user", String.valueOf(user.id()), user.email());
        // Whoever held the old password held every session opened with it.
        sessionService.revokeAllForUser(user.id());
        User updated = userUseCase.findByEmail(user.email()).orElseThrow();
        IssuedSession issued = issueTokens(updated);
        sessionService.create(updated.companyId(), updated.id(), issued.refreshTokenHash(), issued.refreshExpiresAt());
        return issued.response();
    }

    @Override
    public CurrentUserResponse findCurrentUser(long companyId) {
        long userId = currentUser.id()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
        User user = userUseCase.requireUser(companyId, userId);
        var company = companyUseCase.findById(user.companyId());
        return new CurrentUserResponse(user.id(), user.name(), user.username(), user.email(),
                user.roleId(), user.roleCode(), user.roleName(), resolvePermissions(user),
                user.companyId(), company == null ? null : company.name());
    }

    /**
     * A ticket for the caller's own identity only: an API key (no user id) is refused, and
     * the identity is rebuilt from the user's current row and role, like a new token is.
     */
    @Override
    public StreamTicketResponse createStreamTicket(long companyId) {
        long userId = currentUser.id()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
        return streamTicketService.issue(buildPrincipal(userUseCase.requireUser(companyId, userId)));
    }

    private AuthenticatedUser buildPrincipal(User user) {
        return new AuthenticatedUser(user.id(), user.companyId(), user.roleId(),
                user.roleCode(), resolvePermissions(user), user.name(), user.email());
    }

    private IssuedSession issueTokens(User user) {
        IssuedToken issued = jwtTokenService.issue(buildPrincipal(user));

        String refreshToken = Tokens.generate();
        Instant refreshExpiresAt = clock.instant().plus(REFRESH_TTL);

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
