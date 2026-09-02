package uz.murodjon.robotcallv2.user.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
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
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class UserService implements UserUseCase {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final UserRepository repo;
    private final CurrentCompany company;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public UserService(UserRepository repo, CurrentCompany company, CurrentUser currentUser, AuditService audit) {
        this.repo = repo;
        this.company = company;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public List<UserRow> list() {
        return repo.findAll().stream().map(UserRow::of).toList();
    }

    @Override
    public UserRow get(long id) {
        return UserRow.of(requireUser(id));
    }

    @Override
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        return repo.namesByIds(ids);
    }

    @Override
    public InviteUserResponse invite(InviteUserRequest r) {
        requireNotSuperadmin(r.role());
        if (repo.existsByEmail(r.email())) {
            throw new ConflictException(ErrorCode.USER_EMAIL_TAKEN, r.email());
        }
        if (repo.existsByUsername(r.username())) {
            throw new ConflictException(ErrorCode.USER_USERNAME_TAKEN, r.username());
        }
        long id = repo.create(r.name(), r.username(), r.email(), r.role(), UserStatus.INVITED);
        String token = Tokens.generate();
        Instant expiresAt = Instant.now().plus(INVITE_TTL);
        repo.setInviteToken(id, Tokens.hash(token), expiresAt);
        audit.record("USER_INVITE", "user", String.valueOf(id), r.email());
        return new InviteUserResponse(id, r.email(), token, expiresAt);
    }

    @Override
    public UserRow changeRole(long id, UpdateUserRoleRequest r) {
        requireNotSuperadmin(r.role());
        User target = requireUser(id);
        if (target.role() == UserRole.ADMIN && r.role() != UserRole.ADMIN
                && repo.countActiveAdmins(company.id()) <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ROLE_CHANGE_FORBIDDEN);
        }
        repo.updateRole(id, r.role());
        audit.record("USER_ROLE_CHANGE", "user", String.valueOf(id), r.role().name());
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
        guardLastAdmin(target, "block");
        repo.updateStatus(id, UserStatus.BLOCKED);
        audit.record("USER_BLOCK", "user", String.valueOf(id), target.email());
        return UserRow.of(requireUser(id));
    }

    private UserRow unblock(long id) {
        requireUser(id);
        repo.updateStatus(id, UserStatus.ACTIVE);
        audit.record("USER_UNBLOCK", "user", String.valueOf(id), null);
        return UserRow.of(requireUser(id));
    }

    @Override
    public User requireUser(long id) {
        User row = repo.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, id);
        }
        return row;
    }

    private static void requireNotSuperadmin(UserRole role) {
        if (role == UserRole.SUPERADMIN) {
            throw new ValidationException(ErrorCode.SUPERADMIN_GRANT_FORBIDDEN);
        }
    }

    private void guardLastAdmin(User target, String action) {
        if (currentUser.id().isPresent() && currentUser.id().get() == target.id()) {
            throw new ConflictException(ErrorCode.SELF_ACTION_FORBIDDEN, action);
        }
        if (target.role() == UserRole.ADMIN && target.status() == UserStatus.ACTIVE
                && repo.countActiveAdmins(company.id()) <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ACTION_FORBIDDEN, action);
        }
    }
}
