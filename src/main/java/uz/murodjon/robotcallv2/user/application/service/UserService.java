package uz.murodjon.robotcallv2.user.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.role.domain.service.RoleValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.Tokens;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserAction;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;
import uz.murodjon.robotcallv2.user.domain.event.UserBlockedEvent;
import uz.murodjon.robotcallv2.user.domain.event.UserPasswordResetByAdminEvent;
import uz.murodjon.robotcallv2.user.domain.event.UserRoleChangedEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService implements UserUseCase {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final UserRepository repository;
    private final RoleUseCase roleUseCase;
    /** Sessions belong to auth, which depends on this feature — it hears of a block or role change by event. */
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUser currentUser;
    private final AuditUseCase audit;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, RoleUseCase roleUseCase, ApplicationEventPublisher eventPublisher,
                       CurrentUser currentUser, AuditUseCase audit, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.roleUseCase = roleUseCase;
        this.eventPublisher = eventPublisher;
        this.currentUser = currentUser;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserRow> list(long companyId) {
        return repository.findAll(companyId).stream().map(UserRow::of).toList();
    }

    @Override
    public UserRow requireUserRow(long companyId, long id) {
        return UserRow.of(requireUser(companyId, id));
    }

    @Override
    public Map<Long, String> findNamesByIds(long companyId, Collection<Long> ids) {
        return repository.findNamesByIds(companyId, ids);
    }

    @Override
    public InviteUserResponse invite(long companyId, InviteUserRequest request) {
        Role role = requireAssignableRole(companyId, request.roleId());
        return createInvited(companyId, request, role);
    }

    @Override
    public InviteUserResponse inviteFirstAdmin(long companyId, String name, String username, String email) {
        // Nobody is logged in to vouch for this invite, so it is only allowed where there is
        // nobody yet — a company that already has a user grows through the normal invite.
        if (repository.hasAnyUser(companyId)) {
            throw new ConflictException(ErrorCode.COMPANY_ALREADY_HAS_USERS, companyId);
        }
        Role admin = roleUseCase.findRoleByCode(companyId, SystemRole.ADMIN.name());
        if (admin == null) {
            throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, SystemRole.ADMIN.name());
        }
        return createInvited(companyId, new InviteUserRequest(name, username, email, admin.id()), admin);
    }

    private InviteUserResponse createInvited(long companyId, InviteUserRequest request, Role role) {
        if (repository.existsByEmail(request.email())) {
            throw new ConflictException(ErrorCode.USER_EMAIL_TAKEN, request.email());
        }
        if (repository.existsByUsername(request.username())) {
            throw new ConflictException(ErrorCode.USER_USERNAME_TAKEN, request.username());
        }
        long id = repository.create(companyId, request.name(), request.username(), request.email(), null, role.id(),
                UserStatus.INVITED);
        String token = Tokens.generate();
        Instant expiresAt = Instant.now().plus(INVITE_TTL);
        repository.setInviteToken(id, Tokens.hash(token), expiresAt);
        audit.record(companyId, "USER_INVITE", "user", String.valueOf(id), request.email());
        return new InviteUserResponse(id, request.email(), token, expiresAt);
    }

    @Override
    public UserRow changeRole(long companyId, long id, UpdateUserRoleRequest request) {
        if (currentUser.id().isPresent() && currentUser.id().get() == id) {
            throw new ForbiddenException(ErrorCode.SELF_ACTION_FORBIDDEN, UserAction.CHANGE_ROLE);
        }
        Role role = requireAssignableRole(companyId, request.roleId());
        User target = requireUser(companyId, id);
        if (losesUserManagement(companyId, target, role) && countActiveUserManagers(companyId) <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ROLE_CHANGE_FORBIDDEN);
        }
        repository.updateRole(companyId, id, role.id());
        // The old permissions are already inside the token this user is holding.
        eventPublisher.publishEvent(new UserRoleChangedEvent(companyId, id));
        audit.record(companyId, "USER_ROLE_CHANGE", "user", String.valueOf(id), role.name());
        return UserRow.of(requireUser(companyId, id));
    }

    @Override
    public UserRow update(long companyId, long id, UpdateUserRequest request) {
        User target = requireUser(companyId, id);
        // Email and username are unique across the whole platform; keeping one's own value is not a clash.
        repository.findByEmail(request.email())
                .filter(other -> other.id() != id)
                .ifPresent(other -> {
                    throw new ConflictException(ErrorCode.USER_EMAIL_TAKEN, request.email());
                });
        repository.findByUsername(request.username())
                .filter(other -> other.id() != id)
                .ifPresent(other -> {
                    throw new ConflictException(ErrorCode.USER_USERNAME_TAKEN, request.username());
                });
        // Colleagues dial the extension, so two people in one company cannot share it.
        String sipExtension = blankToNull(request.sipExtension());
        if (sipExtension != null) {
            User holder = repository.findBySipExtension(sipExtension, companyId);
            if (holder != null && holder.id() != id) {
                throw new ConflictException(ErrorCode.USER_SIP_EXTENSION_TAKEN, sipExtension);
            }
        }
        // A new login name alone needs no session revocation: the token's subject is the user id.
        repository.updateAccount(companyId, id, request.name(), request.username(), request.email(),
                blankToNull(request.phone()), blankToNull(request.position()), sipExtension);
        audit.record(companyId, "USER_UPDATE", "user", String.valueOf(id), target.email() + " -> " + request.email());

        if (request.newPassword() != null) {
            String passwordHash = passwordEncoder.encode(request.newPassword());
            if (target.status() == UserStatus.INVITED) {
                // The admin is handing over working credentials, so the pending invite is no longer needed.
                repository.activate(id, passwordHash);
            } else {
                repository.updatePassword(companyId, id, passwordHash);
            }
            // Whoever knew the old password may hold a session opened with it.
            eventPublisher.publishEvent(new UserPasswordResetByAdminEvent(companyId, id));
            audit.record(companyId, "USER_PASSWORD_RESET_BY_ADMIN", "user", String.valueOf(id), null);
        }
        return UserRow.of(requireUser(companyId, id));
    }

    @Override
    public UserRow setStatus(long companyId, long id, UserStatus status) {
        if (status == UserStatus.BLOCKED) {
            return block(companyId, id);
        } else if (status == UserStatus.ACTIVE) {
            return unblock(companyId, id);
        }
        throw new ValidationException(ErrorCode.USER_STATUS_UNSUPPORTED, status);
    }

    private UserRow block(long companyId, long id) {
        User target = requireUser(companyId, id);
        guardLastUserManager(companyId, target, UserAction.BLOCK);
        repository.updateStatus(companyId, id, UserStatus.BLOCKED);
        // A pending invite or reset link would otherwise let the blocked user set a password
        // and walk straight back in.
        repository.setInviteToken(id, null, null);
        repository.setResetToken(id, null, null);
        eventPublisher.publishEvent(new UserBlockedEvent(companyId, id));
        audit.record(companyId, "USER_BLOCK", "user", String.valueOf(id), target.email());
        return UserRow.of(requireUser(companyId, id));
    }

    private UserRow unblock(long companyId, long id) {
        requireUser(companyId, id);
        repository.updateStatus(companyId, id, UserStatus.ACTIVE);
        audit.record(companyId, "USER_UNBLOCK", "user", String.valueOf(id), null);
        return UserRow.of(requireUser(companyId, id));
    }

    @Override
    public User requireUser(long companyId, long id) {
        User row = repository.find(companyId, id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public User findBySipExtension(long companyId, String extension) {
        return repository.findBySipExtension(extension, companyId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public Optional<User> findById(long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<User> findByInviteTokenHash(String tokenHash) {
        return repository.findByInviteTokenHash(tokenHash);
    }

    @Override
    public Optional<User> findByResetTokenHash(String tokenHash) {
        return repository.findByResetTokenHash(tokenHash);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public void activate(long id, String passwordHash) {
        repository.activate(id, passwordHash);
    }

    @Override
    public void setResetToken(long id, String tokenHash, Instant expiresAt) {
        repository.setResetToken(id, tokenHash, expiresAt);
    }

    @Override
    public void resetPassword(long id, String passwordHash) {
        repository.resetPassword(id, passwordHash);
    }

    @Override
    public void touchLastLogin(long id) {
        repository.touchLastLogin(id);
    }

    @Override
    public void updateProfile(long companyId, long id, String name, String email, String phone, String position,
                              String sipExtension) {
        repository.updateProfile(companyId, id, name, email, phone, position, sipExtension);
    }

    @Override
    public void updateAvatarFileId(long companyId, long id, Long avatarFileId) {
        repository.updateAvatarFileId(companyId, id, avatarFileId);
    }

    @Override
    public void updatePassword(long companyId, long id, String passwordHash) {
        repository.updatePassword(companyId, id, passwordHash);
    }

    @Override
    public void updateCallColumns(long companyId, long id, String callColumns) {
        repository.updateCallColumns(companyId, id, callColumns);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The role has to exist in this company, and DEVELOPER/SUPERADMIN need platform staff. */
    private Role requireAssignableRole(long companyId, long roleId) {
        Role role = roleUseCase.findRole(companyId, roleId);
        if (role == null) {
            throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, roleId);
        }
        RoleValidator.validateAssignable(role, currentUser.hasPermission(Permission.PLATFORM_ADMIN));
        RoleValidator.validateGrantable(role.permissions(), currentUser::hasPermission);
        return role;
    }

    private void guardLastUserManager(long companyId, User target, UserAction action) {
        if (currentUser.id().isPresent() && currentUser.id().get() == target.id()) {
            throw new ConflictException(ErrorCode.SELF_ACTION_FORBIDDEN, action);
        }
        if (target.status() == UserStatus.ACTIVE && managesUsers(companyId, target.roleId())
                && countActiveUserManagers(companyId) <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ACTION_FORBIDDEN, action);
        }
    }

    private boolean losesUserManagement(long companyId, User target, Role newRole) {
        return managesUsers(companyId, target.roleId()) && !newRole.hasPermission(Permission.USER_EDIT);
    }

    private boolean managesUsers(long companyId, long roleId) {
        Role role = roleUseCase.findRole(companyId, roleId);
        return role != null && role.hasPermission(Permission.USER_EDIT);
    }

    /**
     * How many active users could still administer this company. A company that locks
     * itself out of user management can only be recovered from the database.
     */
    private long countActiveUserManagers(long companyId) {
        List<Long> roleIds = roleUseCase.findRolesWithPermission(companyId, Permission.USER_EDIT).stream()
                .map(Role::id)
                .toList();
        return repository.countActiveByRoleIds(companyId, roleIds);
    }
}
