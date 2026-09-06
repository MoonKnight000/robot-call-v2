package uz.murodjon.robotcallv2.user.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.service.RoleValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class UserService implements UserUseCase {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final UserRepository repository;
    private final RoleUseCase roleUseCase;
    private final SessionUseCase sessionUseCase;
    private final CurrentCompany company;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public UserService(UserRepository repository, RoleUseCase roleUseCase, SessionUseCase sessionUseCase,
                       CurrentCompany company, CurrentUser currentUser, AuditService audit) {
        this.repository = repository;
        this.roleUseCase = roleUseCase;
        this.sessionUseCase = sessionUseCase;
        this.company = company;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public List<UserRow> list() {
        return repository.findAll().stream().map(UserRow::of).toList();
    }

    @Override
    public UserRow get(long id) {
        return UserRow.of(requireUser(id));
    }

    @Override
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        return repository.namesByIds(ids);
    }

    @Override
    public InviteUserResponse invite(InviteUserRequest r) {
        Role role = requireAssignableRole(r.roleId());
        if (repository.existsByEmail(r.email())) {
            throw new ConflictException(ErrorCode.USER_EMAIL_TAKEN, r.email());
        }
        if (repository.existsByUsername(r.username())) {
            throw new ConflictException(ErrorCode.USER_USERNAME_TAKEN, r.username());
        }
        long id = repository.create(r.name(), r.username(), r.email(), role.id(), UserStatus.INVITED);
        String token = Tokens.generate();
        Instant expiresAt = Instant.now().plus(INVITE_TTL);
        repository.setInviteToken(id, Tokens.hash(token), expiresAt);
        audit.record("USER_INVITE", "user", String.valueOf(id), r.email());
        return new InviteUserResponse(id, r.email(), token, expiresAt);
    }

    @Override
    public UserRow changeRole(long id, UpdateUserRoleRequest r) {
        Role role = requireAssignableRole(r.roleId());
        User target = requireUser(id);
        if (losesUserManagement(target, role) && countActiveUserManagers() <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ROLE_CHANGE_FORBIDDEN);
        }
        repository.updateRole(id, role.id());
        // The old permissions are already inside the token this user is holding.
        sessionUseCase.revokeAllForUser(id);
        audit.record("USER_ROLE_CHANGE", "user", String.valueOf(id), role.name());
        return UserRow.of(requireUser(id));
    }

    @Override
    public UserRow setStatus(long id, UserStatus status) {
        if (status == UserStatus.BLOCKED) {
            return block(id);
        } else if (status == UserStatus.ACTIVE) {
            return unblock(id);
        }
        throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Unsupported status transition: " + status);
    }

    private UserRow block(long id) {
        User target = requireUser(id);
        guardLastUserManager(target, "block");
        repository.updateStatus(id, UserStatus.BLOCKED);
        sessionUseCase.revokeAllForUser(id);
        audit.record("USER_BLOCK", "user", String.valueOf(id), target.email());
        return UserRow.of(requireUser(id));
    }

    private UserRow unblock(long id) {
        requireUser(id);
        repository.updateStatus(id, UserStatus.ACTIVE);
        audit.record("USER_UNBLOCK", "user", String.valueOf(id), null);
        return UserRow.of(requireUser(id));
    }

    @Override
    public User requireUser(long id) {
        User row = repository.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, id);
        }
        return row;
    }

    /** The role has to exist in this company, and DEVELOPER/SUPERADMIN need platform staff. */
    private Role requireAssignableRole(long roleId) {
        Role role = roleUseCase.findRole(company.id(), roleId);
        if (role == null) {
            throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, roleId);
        }
        RoleValidator.validateAssignable(role, currentUser.hasPermission(Permission.PLATFORM_ADMIN));
        return role;
    }

    private void guardLastUserManager(User target, String action) {
        if (currentUser.id().isPresent() && currentUser.id().get() == target.id()) {
            throw new ConflictException(ErrorCode.SELF_ACTION_FORBIDDEN, action);
        }
        if (target.status() == UserStatus.ACTIVE && managesUsers(target.roleId())
                && countActiveUserManagers() <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ACTION_FORBIDDEN, action);
        }
    }

    private boolean losesUserManagement(User target, Role newRole) {
        return managesUsers(target.roleId()) && !newRole.hasPermission(Permission.USER_EDIT);
    }

    private boolean managesUsers(long roleId) {
        Role role = roleUseCase.findRole(company.id(), roleId);
        return role != null && role.hasPermission(Permission.USER_EDIT);
    }

    /**
     * How many active users could still administer this company. A company that locks
     * itself out of user management can only be recovered from the database.
     */
    private long countActiveUserManagers() {
        long companyId = company.id();
        List<Long> roleIds = roleUseCase.findRolesWithPermission(companyId, Permission.USER_EDIT).stream()
                .map(Role::id)
                .toList();
        return repository.countActiveByRoleIds(companyId, roleIds);
    }
}
