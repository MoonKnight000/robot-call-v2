package uz.murodjon.robotcallv2.role.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.role.application.port.input.RoleTemplateUseCase;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.application.port.output.RoleRepository;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionGroup;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionScope;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.role.domain.event.RoleChangedEvent;
import uz.murodjon.robotcallv2.role.domain.service.RoleValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class RoleService implements RoleUseCase {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository repository;
    private final RoleTemplateUseCase roleTemplateUseCase;
    /** Sessions belong to auth, which depends on this feature — it hears of an edited role by event. */
    private final ApplicationEventPublisher eventPublisher;
    private final AuditUseCase audit;
    private final CurrentUser currentUser;

    public RoleService(RoleRepository repository, RoleTemplateUseCase roleTemplateUseCase,
                       ApplicationEventPublisher eventPublisher, AuditUseCase audit, CurrentUser currentUser) {
        this.repository = repository;
        this.roleTemplateUseCase = roleTemplateUseCase;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Override
    public List<RoleRow> findByCompanyId(long companyId) {
        Map<Long, Long> userCounts = repository.countUsersByRole(companyId);
        return repository.findByCompanyId(companyId).stream()
                .map(role -> RoleRow.of(role, userCounts.getOrDefault(role.id(), 0L)))
                .toList();
    }

    @Override
    public RoleRow requireRoleRow(long companyId, long id) {
        Role role = requireRole(companyId, id);
        return RoleRow.of(role, repository.countUsersByRole(companyId).getOrDefault(id, 0L));
    }

    @Override
    public RoleRow create(long companyId, CreateRoleRequest request) {
        RoleValidator.validateCreate(request.permissions(), repository.countByCompanyId(companyId));
        RoleValidator.validateGrantable(request.permissions(), currentUser::hasPermission);
        requireNameFree(companyId, request.name(), null);
        Role created = repository.create(companyId,
                Role.custom(request.name(), request.description(), request.permissions()));
        audit.record(companyId, "ROLE_CREATE", "role", String.valueOf(created.id()), created.name());
        return RoleRow.of(created, 0L);
    }

    @Override
    public RoleRow update(long companyId, long id, UpdateRoleRequest request) {
        Role existing = requireRole(companyId, id);
        RoleValidator.validateEditable(existing, isPlatformAdmin());
        RoleValidator.validatePermissions(request.permissions());
        RoleValidator.validateGrantable(request.permissions(), currentUser::hasPermission);
        requireNameFree(companyId, request.name(), id);
        RoleValidator.validateKeepsUserManager(existing, request.permissions(),
                repository.countActiveUsersByRoleIds(companyId, List.of(id)),
                countOtherActiveUserManagers(companyId, id));
        // The code stays: it is how the company's ADMIN role is still found after a rename.
        Role updated = repository.update(companyId,
                new Role(id, companyId, existing.code(), request.name(), request.description(),
                        existing.assignableByCompany(), request.permissions(), existing.createdAt()));
        // Permissions travel inside the access token, so a token issued a minute ago still
        // carries the old set — the only honest way to apply a narrowed role immediately is
        // to make its holders log in again.
        revokeSessions(companyId, id);
        audit.record(companyId, "ROLE_UPDATE", "role", String.valueOf(id), updated.name());
        return RoleRow.of(updated, repository.countUsersByRole(companyId).getOrDefault(id, 0L));
    }

    @Override
    public void delete(long companyId, long id) {
        Role existing = requireRole(companyId, id);
        RoleValidator.validateEditable(existing, isPlatformAdmin());
        RoleValidator.validateNotInUse(existing, repository.countUsersByRole(companyId).getOrDefault(id, 0L));
        repository.delete(companyId, id);
        audit.record(companyId, "ROLE_DELETE", "role", String.valueOf(id), existing.name());
    }

    @Override
    public List<PermissionGroupRow> listPermissions() {
        List<PermissionGroupRow> groups = new ArrayList<>();
        for (PermissionGroup group : PermissionGroup.values()) {
            List<Permission> permissions = Arrays.stream(Permission.values())
                    .filter(permission -> permission.scope() == PermissionScope.COMPANY)
                    .filter(permission -> permission.group() == group)
                    .toList();
            if (!permissions.isEmpty()) {
                groups.add(new PermissionGroupRow(group, group.feature(), permissions));
            }
        }
        return groups;
    }

    @Override
    public Role findRole(long companyId, long roleId) {
        return repository.find(companyId, roleId);
    }

    @Override
    public Role findRoleByCode(long companyId, String code) {
        return repository.findByCode(companyId, code);
    }

    @Override
    public List<Role> findRolesWithPermission(long companyId, Permission permission) {
        return repository.findByCompanyId(companyId).stream()
                .filter(role -> role.hasPermission(permission))
                .toList();
    }

    @Override
    public void createRolesFromTemplates(long companyId) {
        // Only a company with no roles at all: one that has them has already been seeded and may
        // have deleted a template's copy on purpose.
        if (repository.countByCompanyId(companyId) > 0) {
            return;
        }
        for (RoleTemplate template : roleTemplateUseCase.findTemplates()) {
            repository.create(companyId, Role.fromTemplate(template));
        }
    }

    @Override
    public void materializeLegacySystemRoles() {
        int changed = 0;
        for (SystemRole systemRole : SystemRole.values()) {
            changed += repository.materializeLegacySystemRole(systemRole.name(), systemRole.resolvePermissions());
        }
        if (changed > 0) {
            log.info("Wrote down the permissions of {} code-computed role(s); they are editable now", changed);
        }
    }

    private boolean isPlatformAdmin() {
        return currentUser.hasPermission(Permission.PLATFORM_ADMIN);
    }

    /** Active users who could still manage users without the role {@code excludeRoleId}. */
    private long countOtherActiveUserManagers(long companyId, long excludeRoleId) {
        List<Long> roleIds = findRolesWithPermission(companyId, Permission.USER_EDIT).stream()
                .map(Role::id)
                .filter(roleId -> roleId != excludeRoleId)
                .toList();
        return repository.countActiveUsersByRoleIds(companyId, roleIds);
    }

    private Role requireRole(long companyId, long id) {
        Role role = repository.find(companyId, id);
        if (role == null) {
            throw new NotFoundException(ErrorCode.ROLE_NOT_FOUND, id);
        }
        return role;
    }

    private void requireNameFree(long companyId, String name, Long excludeId) {
        if (repository.existsByName(companyId, name, excludeId)) {
            throw new ConflictException(ErrorCode.ROLE_NAME_TAKEN, name);
        }
    }

    private void revokeSessions(long companyId, long roleId) {
        eventPublisher.publishEvent(
                new RoleChangedEvent(companyId, roleId, repository.findUserIdsByRoleId(companyId, roleId)));
    }
}
