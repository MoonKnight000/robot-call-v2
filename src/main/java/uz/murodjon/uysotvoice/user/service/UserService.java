package uz.murodjon.uysotvoice.user.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.Tokens;
import uz.murodjon.uysotvoice.user.domain.User;
import uz.murodjon.uysotvoice.user.dto.InviteUserRequest;
import uz.murodjon.uysotvoice.user.dto.InviteUserResponse;
import uz.murodjon.uysotvoice.user.dto.UserRow;
import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.enums.UserStatus;
import uz.murodjon.uysotvoice.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Foydalanuvchi CRUD (ROADMAP E.1): invite, rol o'zgartirish, block/unblock. Login va
 * aktivatsiya — {@code auth.service.AuthService} da (bu servis hisob yaratadi, u hisob
 * bilan kiradi).
 */
@Service
public class UserService {

    /** {@code POST /api/auth/activate} uchun amal muddati — email yo'q, shuning uchun sal keng. */
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

    public List<UserRow> list() {
        return repo.findAll().stream().map(UserRow::of).toList();
    }

    /**
     * Cheap id→name lookup for other features to enrich rows with e.g. {@code createdByName}
     * (see {@code CampaignRow}).
     */
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        return repo.namesByIds(ids);
    }

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
        repo.setInviteToken(id, Tokens.hash(token), Instant.now().plus(INVITE_TTL));
        audit.record("USER_INVITE", "user", String.valueOf(id), r.email());
        return new InviteUserResponse(UserRow.of(requireUser(id)), token);
    }

    public UserRow changeRole(long id, UserRole role) {
        requireNotSuperadmin(role);
        User target = requireUser(id);
        if (target.role() == UserRole.ADMIN && role != UserRole.ADMIN
                && repo.countActiveAdmins(company.id()) <= 1) {
            throw new ConflictException(ErrorCode.LAST_ADMIN_ROLE_CHANGE_FORBIDDEN);
        }
        repo.updateRole(id, role);
        audit.record("USER_ROLE_CHANGE", "user", String.valueOf(id), role.name());
        return UserRow.of(requireUser(id));
    }

    /**
     * {@code SUPERADMIN} is platform staff, not a tenant role (report #3) — never
     * settable through this tenant-scoped ({@code ADMIN}-gated, per-company) service.
     * The first superadmin account is a one-time manual {@code app_user} row.
     */
    private static void requireNotSuperadmin(UserRole role) {
        if (role == UserRole.SUPERADMIN) {
            throw new ValidationException(ErrorCode.SUPERADMIN_GRANT_FORBIDDEN);
        }
    }

    public UserRow block(long id) {
        User target = requireUser(id);
        guardLastAdmin(target, "block");
        repo.updateStatus(id, UserStatus.BLOCKED);
        audit.record("USER_BLOCK", "user", String.valueOf(id), target.email());
        return UserRow.of(requireUser(id));
    }

    public UserRow unblock(long id) {
        requireUser(id);
        repo.updateStatus(id, UserStatus.ACTIVE);
        audit.record("USER_UNBLOCK", "user", String.valueOf(id), null);
        return UserRow.of(requireUser(id));
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

    private User requireUser(long id) {
        User row = repo.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, id);
        }
        return row;
    }
}
