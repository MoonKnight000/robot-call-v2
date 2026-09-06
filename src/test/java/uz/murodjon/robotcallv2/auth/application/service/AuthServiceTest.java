package uz.murodjon.robotcallv2.auth.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.dto.IssuedToken;
import uz.murodjon.robotcallv2.auth.application.dto.LoginRequest;
import uz.murodjon.robotcallv2.auth.application.dto.LoginResponse;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private static final Role ADMIN_ROLE = new Role(1L, 1L, SystemRole.ADMIN.name(), SystemRole.ADMIN.label(),
            null, true, Set.of(), Instant.now());

    private UserRepository users;
    private CompanyRepository companies;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService tokens;
    private CurrentUser currentUser;
    private AuditService audit;
    private SessionService sessions;
    private PasswordResetMailSender resetMail;
    private RoleUseCase roleUseCase;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        companies = mock(CompanyRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokens = mock(JwtTokenService.class);
        currentUser = mock(CurrentUser.class);
        audit = mock(AuditService.class);
        sessions = mock(SessionService.class);
        resetMail = mock(PasswordResetMailSender.class);
        roleUseCase = mock(RoleUseCase.class);
        when(roleUseCase.findRole(1L, ADMIN_ROLE.id())).thenReturn(ADMIN_ROLE);

        authService = new AuthService(
                users, companies, passwordEncoder, tokens,
                currentUser, audit, sessions, resetMail, roleUseCase
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

        LoginResponse response = authService.login(new LoginRequest("ali", "secret123"));

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("jwt.token.here");
        assertThat(response.user().id()).isEqualTo(1L);

        verify(users).touchLastLogin(1L);
        verify(audit).record(eq("USER_LOGIN"), eq("user"), eq("1"), eq("ali@example.com"));
        verify(sessions).create(eq(1L), eq(1L), any(), any());
    }

    @Test
    void loginFailsWhenUserNotFound() {
        when(users.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "pass")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void loginFailsWhenUserIsBlocked() {
        User blockedUser = sampleUser(UserStatus.BLOCKED, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(blockedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "pass")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void loginFailsWhenUserNotActivated() {
        User invitedUser = sampleUser(UserStatus.INVITED, null);
        when(users.findByUsername("ali")).thenReturn(Optional.of(invitedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "pass")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void loginFailsWhenPasswordDoesNotMatch() {
        User user = sampleUser(UserStatus.ACTIVE, "hashed_pass");
        when(users.findByUsername("ali")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali", "wrong_pass")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void logoutRevokesAllUserSessions() {
        when(currentUser.id()).thenReturn(Optional.of(42L));

        authService.logout();

        verify(sessions).revokeAllForUser(42L);
    }
}
