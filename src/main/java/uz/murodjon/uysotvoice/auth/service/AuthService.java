package uz.murodjon.uysotvoice.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.auth.domain.UserSession;
import uz.murodjon.uysotvoice.auth.dto.ActivateRequest;
import uz.murodjon.uysotvoice.auth.dto.AuthenticatedUser;
import uz.murodjon.uysotvoice.auth.dto.CurrentUserResponse;
import uz.murodjon.uysotvoice.auth.dto.ForgotPasswordRequest;
import uz.murodjon.uysotvoice.auth.dto.IssuedToken;
import uz.murodjon.uysotvoice.auth.dto.LoginRequest;
import uz.murodjon.uysotvoice.auth.dto.LoginResponse;
import uz.murodjon.uysotvoice.auth.dto.RefreshTokenRequest;
import uz.murodjon.uysotvoice.auth.dto.ResetPasswordRequest;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.repository.CompanyRepository;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.Tokens;
import uz.murodjon.uysotvoice.user.domain.User;
import uz.murodjon.uysotvoice.user.dto.UserRow;
import uz.murodjon.uysotvoice.user.enums.UserStatus;
import uz.murodjon.uysotvoice.user.repository.UserRepository;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Login/activation (ROADMAP E.1). {@code app_user} CRUD (invite, role, block/unblock) is
 * {@code user.service.UserService} — this service only ever creates the session, not the
 * account (activation is the one exception: it turns an invited account into a usable one).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Refresh token lifetime — much longer than the access token so a logged-in panel
     * session survives without re-prompting for the password until this expires. */
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    /** {@code POST /api/auth/forgot-password} token lifetime — short, unlike {@code
     * UserService#INVITE_TTL}: this one is emailed straight away, not manually relayed. */
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

    /**
     * {@code GET /api/companies} (UI-DESIGN §7.3 sidebar switcher) — MVP is one user, one
     * company, so this is always a single-element list; ROADMAP E.2 multi-company
     * membership extends this without changing the endpoint shape.
     */
    public List<Company> myCompanies() {
        Company company = companies.find(currentCompany.id());
        return company == null ? List.of() : List.of(company);
    }

    public LoginResponse login(LoginRequest r) {
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

    /**
     * {@code POST /api/auth/refresh} — trades a still-valid refresh token for a new
     * access/refresh pair, rotating the refresh token in place on the same {@code
     * user_session} row so the old one can't be replayed and the device's session
     * identity (for {@code GET /api/profile/sessions}) survives across refreshes.
     */
    public LoginResponse refresh(RefreshTokenRequest r) {
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

    /** {@code POST /api/auth/logout} — the request names no device, so every session is revoked. */
    public void logout() {
        currentUser.id().ifPresent(sessions::revokeAll);
    }

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

    /**
     * {@code POST /api/auth/forgot-password} — always completes normally whether or not
     * {@code r.email()} belongs to an account, so the response never reveals which (§9).
     * A matching {@code ACTIVE} account gets a one-time token emailed; the send itself is
     * best-effort — a misconfigured/unreachable SMTP server is logged, not surfaced.
     */
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

    /**
     * {@code POST /api/auth/reset-password} — same one-time-token pattern as
     * {@link #activate}, and likewise logs the account straight in on success. Unlike
     * {@code ProfileService#changePassword}, this does not revoke the account's other
     * sessions — same "don't force a re-login" convention that endpoint already documents.
     */
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

    /** Issues the JWT/refresh-token pair; the caller decides whether to {@code create} or {@code rotate} the session. */
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
