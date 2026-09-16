package uz.murodjon.robotcallv2.auth.application.service;

import uz.murodjon.robotcallv2.auth.application.port.input.TwoFactorUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.dto.ActivateRequest;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.IssuedToken;
import uz.murodjon.robotcallv2.auth.application.dto.LoginRequest;
import uz.murodjon.robotcallv2.auth.application.dto.LoginResponse;
import uz.murodjon.robotcallv2.auth.application.dto.RefreshTokenRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.security.RateLimitProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.TooManyRequestsException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private static final Role ADMIN_ROLE = new Role(1L, 1L, SystemRole.ADMIN.name(), SystemRole.ADMIN.label(),
            null, true, Set.of(), Instant.now());
    private static final String ADDRESS = "10.0.0.1";
    private static final String FAILURES_KEY = "auth:login-fail:ali:" + ADDRESS;

    private UserUseCase users;
    private CompanyUseCase companies;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService tokens;
    private CurrentUser currentUser;
    private AuditService audit;
    private SessionService sessions;
    private PasswordResetMailSender resetMail;
    private RoleUseCase roleUseCase;
    private RateLimiter rateLimiter;
    private StreamTicketService streamTicketService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserUseCase.class);
        companies = mock(CompanyUseCase.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokens = mock(JwtTokenService.class);
        currentUser = mock(CurrentUser.class);
        audit = mock(AuditService.class);
        sessions = mock(SessionService.class);
        resetMail = mock(PasswordResetMailSender.class);
        roleUseCase = mock(RoleUseCase.class);
        rateLimiter = mock(RateLimiter.class);
        streamTicketService = mock(StreamTicketService.class);
        when(roleUseCase.findRole(1L, ADMIN_ROLE.id())).thenReturn(ADMIN_ROLE);

        authService = new AuthService(
                users, companies, passwordEncoder, tokens,
                currentUser, audit, sessions, resetMail, roleUseCase,
                rateLimiter, new RateLimitProperties(true, false, 5, 10, 15, 30, 3, 20),
                mock(TwoFactorUseCase.class), streamTicketService
        );
    }

    private User sampleUser(UserStatus status, String passwordHash) {
        return new User(
                1L, 1L, "Ali Valiyev", "ali", "ali@example.com",
                passwordHash, ADMIN_ROLE.id(), ADMIN_ROLE.code(), ADMIN_ROLE.name(), status,
                null, null, null, null, null, Instant.now(),
                "+998901234567", "Manager", null, null, null
        );
    }

    @Test
    void loginSuccessfulWithValidCredentials() {
        User user = sampleUser(UserStatus.ACTIVE, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed_pass")).thenReturn(true);
        when(tokens.issue(any())).thenReturn(new IssuedToken("jwt.token.here", Instant.now().plusSeconds(3600)));

        LoginResponse response = authService.login(new LoginRequest("ali", "secret123"), ADDRESS);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("jwt.token.here");
        assertThat(response.user().id()).isEqualTo(1L);

        verify(users).touchLastLogin(1L);
        verify(audit).record(eq(1L), eq("USER_LOGIN"), eq("user"), eq("1"), eq("ali@example.com"));
        verify(sessions).create(eq(1L), eq(1L), any(), any());
        verify(rateLimiter).clear(FAILURES_KEY);
    }

    @Test
    void loginFailsWhenUserNotFound() {
        when(users.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "pass"), ADDRESS))
                .isInstanceOf(ValidationException.class);
        verify(rateLimiter).hit(eq("auth:login-fail:unknown:" + ADDRESS), any());
    }

    /** A blocked account answers exactly like a wrong password — the response never says which. */
    @Test
    void loginFailsWhenUserIsBlocked() {
        User blockedUser = sampleUser(UserStatus.BLOCKED, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(blockedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "pass"), ADDRESS))
                .isInstanceOf(ValidationException.class);
        verify(audit).record(eq(1L), eq("LOGIN_FAILED"), eq("user"), eq("1"), eq("ACCOUNT_BLOCKED"));
    }

    @Test
    void loginFailsWhenUserNotActivated() {
        User invitedUser = sampleUser(UserStatus.INVITED, null);
        when(users.findByUsername("ali")).thenReturn(Optional.of(invitedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "pass"), ADDRESS))
                .isInstanceOf(ValidationException.class);
        verify(audit).record(eq(1L), eq("LOGIN_FAILED"), eq("user"), eq("1"), eq("ACCOUNT_NOT_ACTIVATED"));
    }

    @Test
    void loginFailsWhenPasswordDoesNotMatch() {
        User user = sampleUser(UserStatus.ACTIVE, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "wrong_pass"), ADDRESS))
                .isInstanceOf(ValidationException.class);
        verify(audit).record(eq(1L), eq("LOGIN_FAILED"), eq("user"), eq("1"), eq("BAD_PASSWORD"));
    }

    @Test
    void tenthFailureLocksTheAccountForThatAddress() {
        User user = sampleUser(UserStatus.ACTIVE, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(rateLimiter.hit(eq(FAILURES_KEY), any())).thenReturn(10L);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "wrong_pass"), ADDRESS))
                .isInstanceOf(ValidationException.class);
        verify(audit).record(eq(1L), eq("LOGIN_LOCKED"), eq("user"), eq("1"), eq("15 min, from " + ADDRESS));
    }

    /** Once locked, the password is not even looked at until the window passes. */
    @Test
    void lockedAccountIsRefusedWithoutCheckingThePassword() {
        when(rateLimiter.count(FAILURES_KEY)).thenReturn(10L);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "secret123"), ADDRESS))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(users, passwordEncoder);
    }

    /** Someone hammering a known username from their own address must not lock its owner out. */
    @Test
    void lockOnOneAddressDoesNotStopTheSameUsernameFromAnother() {
        when(rateLimiter.count(FAILURES_KEY)).thenReturn(10L);
        User user = sampleUser(UserStatus.ACTIVE, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed_pass")).thenReturn(true);
        when(tokens.issue(any())).thenReturn(new IssuedToken("jwt", Instant.now().plusSeconds(3600)));

        LoginResponse response = authService.login(new LoginRequest("ali", "secret123"), "10.0.0.2");

        assertThat(response.accessToken()).isEqualTo("jwt");
        verify(rateLimiter).count("auth:login-fail:ali:10.0.0.2");
        verify(rateLimiter).clear("auth:login-fail:ali:10.0.0.2");
        verify(rateLimiter, never()).clear(FAILURES_KEY);
    }

    @Test
    void refreshTakesTheTokenFromTheCookieWhenTheBodyHasNone() {
        assertThatThrownBy(() -> authService.refresh(null, "cookie-token"))
                .isInstanceOf(ForbiddenException.class);
        verify(sessions).findActiveByHash(Tokens.hash("cookie-token"));
    }

    @Test
    void refreshPrefersATokenInTheBody() {
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("body-token"), "cookie-token"))
                .isInstanceOf(ForbiddenException.class);
        verify(sessions).findActiveByHash(Tokens.hash("body-token"));
        verify(sessions, never()).findActiveByHash(Tokens.hash("cookie-token"));
    }

    @Test
    void refreshWithoutAnyTokenIsA400() {
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(" "), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(ErrorCode.REFRESH_TOKEN_MISSING.format());
        verifyNoInteractions(sessions);
    }

    /**
     * The only caller of UserUseCase.activate is this check — which is why UserService.activate
     * carries no status guard of its own.
     */
    @Test
    void activateRefusesAnAccountThatIsNoLongerInvited() {
        User blocked = sampleUser(UserStatus.BLOCKED, "hashed_pass");
        when(users.findByInviteTokenHash(Tokens.hash("invite-token"))).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> authService.activate(new ActivateRequest("invite-token", "NewPassword123")))
                .isInstanceOf(ValidationException.class);
        verify(users, never()).activate(anyLong(), any());
    }

    @Test
    void streamTicketIsIssuedForTheCallersCurrentIdentity() {
        when(currentUser.id()).thenReturn(Optional.of(1L));
        when(users.requireUser(1L, 1L)).thenReturn(sampleUser(UserStatus.ACTIVE, "hashed_pass"));

        authService.createStreamTicket(1L);

        verify(streamTicketService).issue(new AuthenticatedUser(1L, 1L, ADMIN_ROLE.id(), ADMIN_ROLE.code(),
                ADMIN_ROLE.permissions(), "Ali Valiyev", "ali@example.com"));
    }

    /** An API key has no user behind it, and a ticket is only ever for a person. */
    @Test
    void streamTicketIsRefusedWithoutAUserSession() {
        when(currentUser.id()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.createStreamTicket(1L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(streamTicketService);
    }

    @Test
    void logoutRevokesAllUserSessions() {
        when(currentUser.id()).thenReturn(Optional.of(42L));

        authService.logout();

        verify(sessions).revokeAllForUser(42L);
    }
}
