package uz.murodjon.robotcallv2.user.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final Role ADMIN_ROLE = systemRole(1L, SystemRole.ADMIN);
    private static final Role OPERATOR_ROLE = systemRole(2L, SystemRole.OPERATOR);
    private static final Role DEVELOPER_ROLE = systemRole(3L, SystemRole.DEVELOPER);

    private UserRepository repository;
    private RoleUseCase roleUseCase;
    private SessionUseCase sessionUseCase;
    private CurrentCompany company;
    private CurrentUser currentUser;
    private AuditService audit;
    private UserService userService;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        roleUseCase = mock(RoleUseCase.class);
        sessionUseCase = mock(SessionUseCase.class);
        company = mock(CurrentCompany.class);
        currentUser = mock(CurrentUser.class);
        audit = mock(AuditService.class);

        when(company.id()).thenReturn(COMPANY_ID);
        when(currentUser.id()).thenReturn(Optional.of(100L));
        when(roleUseCase.findRole(COMPANY_ID, ADMIN_ROLE.id())).thenReturn(ADMIN_ROLE);
        when(roleUseCase.findRole(COMPANY_ID, OPERATOR_ROLE.id())).thenReturn(OPERATOR_ROLE);
        when(roleUseCase.findRole(COMPANY_ID, DEVELOPER_ROLE.id())).thenReturn(DEVELOPER_ROLE);

        userService = new UserService(repository, roleUseCase, sessionUseCase, company, currentUser, audit);
    }

    private static Role systemRole(long id, SystemRole systemRole) {
        return new Role(id, COMPANY_ID, systemRole.name(), systemRole.label(), null, true, Set.of(), Instant.now());
    }

    private static User sampleUser(long id, Role role, UserStatus status) {
        return new User(
                id, COMPANY_ID, "User " + id, "user" + id, "user" + id + "@example.com", "hash",
                role.id(), role.code(), role.name(), status,
                null, null, null, null, null, Instant.now(),
                null, null, null, null, null
        );
    }

    @Test
    void inviteUserSuccess() {
        InviteUserRequest req = new InviteUserRequest("Jasur", "jasur", "jasur@example.com", OPERATOR_ROLE.id());

        when(repository.existsByEmail("jasur@example.com")).thenReturn(false);
        when(repository.existsByUsername("jasur")).thenReturn(false);
        when(repository.create("Jasur", "jasur", "jasur@example.com", OPERATOR_ROLE.id(), UserStatus.INVITED))
                .thenReturn(5L);

        InviteUserResponse res = userService.invite(req);

        assertThat(res).isNotNull();
        assertThat(res.userId()).isEqualTo(5L);
        assertThat(res.email()).isEqualTo("jasur@example.com");
        assertThat(res.inviteToken()).isNotBlank();

        verify(repository).setInviteToken(eq(5L), anyString(), any(Instant.class));
        verify(audit).record("USER_INVITE", "user", "5", "jasur@example.com");
    }

    @Test
    void inviteUserThrowsConflictWhenEmailExists() {
        InviteUserRequest req = new InviteUserRequest("Jasur", "jasur", "jasur@example.com", OPERATOR_ROLE.id());
        when(repository.existsByEmail("jasur@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.invite(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void inviteUserThrowsNotFoundWhenRoleBelongsToAnotherCompany() {
        InviteUserRequest req = new InviteUserRequest("Jasur", "jasur", "jasur@example.com", 99L);
        when(roleUseCase.findRole(COMPANY_ID, 99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.invite(req))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void inviteUserThrowsForbiddenWhenGrantingDeveloperWithoutPlatformAdmin() {
        InviteUserRequest req = new InviteUserRequest("Root", "root", "root@example.com", DEVELOPER_ROLE.id());
        when(currentUser.hasPermission(Permission.PLATFORM_ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> userService.invite(req))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void changeRoleFailsWhenDemotingLastUserManager() {
        User admin = sampleUser(1L, ADMIN_ROLE, UserStatus.ACTIVE);
        when(repository.find(1L)).thenReturn(admin);
        when(roleUseCase.findRolesWithPermission(COMPANY_ID, Permission.USER_EDIT))
                .thenReturn(List.of(ADMIN_ROLE));
        when(repository.countActiveByRoleIds(COMPANY_ID, List.of(ADMIN_ROLE.id()))).thenReturn(1L);

        UpdateUserRoleRequest req = new UpdateUserRoleRequest(OPERATOR_ROLE.id());

        assertThatThrownBy(() -> userService.changeRole(1L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void changeRoleRevokesTheSessionsOfTheUser() {
        User operator = sampleUser(5L, OPERATOR_ROLE, UserStatus.ACTIVE);
        when(repository.find(5L)).thenReturn(operator);
        when(roleUseCase.findRolesWithPermission(COMPANY_ID, Permission.USER_EDIT))
                .thenReturn(List.of(ADMIN_ROLE));
        when(repository.countActiveByRoleIds(COMPANY_ID, List.of(ADMIN_ROLE.id()))).thenReturn(2L);

        userService.changeRole(5L, new UpdateUserRoleRequest(ADMIN_ROLE.id()));

        verify(repository).updateRole(5L, ADMIN_ROLE.id());
        verify(sessionUseCase).revokeAllForUser(5L);
    }

    @Test
    void blockFailsOnSelfAction() {
        when(currentUser.id()).thenReturn(Optional.of(1L));
        User admin = sampleUser(1L, ADMIN_ROLE, UserStatus.ACTIVE);
        when(repository.find(1L)).thenReturn(admin);

        assertThatThrownBy(() -> userService.setStatus(1L, UserStatus.BLOCKED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void blockFailsOnLastUserManager() {
        when(currentUser.id()).thenReturn(Optional.of(100L));
        User admin = sampleUser(1L, ADMIN_ROLE, UserStatus.ACTIVE);
        when(repository.find(1L)).thenReturn(admin);
        when(roleUseCase.findRolesWithPermission(COMPANY_ID, Permission.USER_EDIT))
                .thenReturn(List.of(ADMIN_ROLE));
        when(repository.countActiveByRoleIds(COMPANY_ID, List.of(ADMIN_ROLE.id()))).thenReturn(1L);

        assertThatThrownBy(() -> userService.setStatus(1L, UserStatus.BLOCKED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unblockSuccess() {
        User blockedUser = sampleUser(5L, OPERATOR_ROLE, UserStatus.BLOCKED);
        User activeUser = sampleUser(5L, OPERATOR_ROLE, UserStatus.ACTIVE);
        when(repository.find(5L)).thenReturn(blockedUser, activeUser);

        UserRow result = userService.setStatus(5L, UserStatus.ACTIVE);

        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        verify(repository).updateStatus(5L, UserStatus.ACTIVE);
        verify(audit).record("USER_UNBLOCK", "user", "5", null);
    }
}
