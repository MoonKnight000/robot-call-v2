package uz.murodjon.robotcallv2.user.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository repo;
    private CurrentCompany company;
    private CurrentUser currentUser;
    private AuditService audit;
    private UserService userService;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        company = mock(CurrentCompany.class);
        currentUser = mock(CurrentUser.class);
        audit = mock(AuditService.class);

        when(company.id()).thenReturn(1L);
        when(currentUser.id()).thenReturn(Optional.of(100L));

        userService = new UserService(repo, company, currentUser, audit);
    }

    private User sampleUser(long id, UserRole role, UserStatus status) {
        return new User(
                id, 1L, "User " + id, "user" + id, "user" + id + "@example.com",
                "hash", role, status,
                null, null, null, null, null, Instant.now(),
                null, null, null, null, null
        );
    }

    @Test
    void inviteUserSuccess() {
        InviteUserRequest req = new InviteUserRequest("Jasur", "jasur", "jasur@example.com", UserRole.OPERATOR);

        when(repo.existsByEmail("jasur@example.com")).thenReturn(false);
        when(repo.existsByUsername("jasur")).thenReturn(false);
        when(repo.create("Jasur", "jasur", "jasur@example.com", UserRole.OPERATOR, UserStatus.INVITED))
                .thenReturn(5L);

        InviteUserResponse res = userService.invite(req);

        assertThat(res).isNotNull();
        assertThat(res.userId()).isEqualTo(5L);
        assertThat(res.email()).isEqualTo("jasur@example.com");
        assertThat(res.inviteToken()).isNotBlank();

        verify(repo).setInviteToken(eq(5L), anyString(), any(Instant.class));
        verify(audit).record("USER_INVITE", "user", "5", "jasur@example.com");
    }

    @Test
    void inviteUserThrowsConflictWhenEmailExists() {
        InviteUserRequest req = new InviteUserRequest("Jasur", "jasur", "jasur@example.com", UserRole.OPERATOR);
        when(repo.existsByEmail("jasur@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.invite(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void inviteUserThrowsValidationWhenInvitingSuperAdmin() {
        InviteUserRequest req = new InviteUserRequest("Root", "root", "root@example.com", UserRole.SUPERADMIN);

        assertThatThrownBy(() -> userService.invite(req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void changeRoleFailsWhenDemotingLastActiveAdmin() {
        User admin = sampleUser(1L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(repo.find(1L)).thenReturn(admin);
        when(repo.countActiveAdmins(1L)).thenReturn(1L); // Only 1 admin exists

        UpdateUserRoleRequest req = new UpdateUserRoleRequest(UserRole.OPERATOR);

        assertThatThrownBy(() -> userService.changeRole(1L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void blockFailsOnSelfAction() {
        when(currentUser.id()).thenReturn(Optional.of(1L));
        User admin = sampleUser(1L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(repo.find(1L)).thenReturn(admin);

        assertThatThrownBy(() -> userService.setStatus(1L, UserStatus.BLOCKED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void blockFailsOnLastActiveAdmin() {
        when(currentUser.id()).thenReturn(Optional.of(100L));
        User admin = sampleUser(1L, UserRole.ADMIN, UserStatus.ACTIVE);
        when(repo.find(1L)).thenReturn(admin);
        when(repo.countActiveAdmins(1L)).thenReturn(1L);

        assertThatThrownBy(() -> userService.setStatus(1L, UserStatus.BLOCKED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unblockSuccess() {
        User blockedUser = sampleUser(5L, UserRole.OPERATOR, UserStatus.BLOCKED);
        User activeUser = sampleUser(5L, UserRole.OPERATOR, UserStatus.ACTIVE);
        when(repo.find(5L)).thenReturn(blockedUser, activeUser);

        UserRow result = userService.setStatus(5L, UserStatus.ACTIVE);

        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        verify(repo).updateStatus(5L, UserStatus.ACTIVE);
        verify(audit).record("USER_UNBLOCK", "user", "5", null);
    }
}
