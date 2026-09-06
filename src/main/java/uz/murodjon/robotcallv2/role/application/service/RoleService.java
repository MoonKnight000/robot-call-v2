package uz.murodjon.robotcallv2.role.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.application.port.output.RoleRepository;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionGroup;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionScope;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.role.domain.service.RoleValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class RoleService implements RoleUseCase {

    private final RoleRepository repository;
    private final CurrentCompany company;
    private final CompanyProperties companyProperties;
    private final SessionUseCase sessionUseCase;
    private final AuditService audit;

    public RoleService(RoleRepository repository, CurrentCompany company, CompanyProperties companyProperties,
                       SessionUseCase sessionUseCase, AuditService audit) {
        this.repository = repository;
        this.company = company;
        this.companyProperties = companyProperties;
        this.sessionUseCase = sessionUseCase;
        this.audit = audit;
    }

    @Override
    public List<RoleRow> listForCurrentCompany() {
        long companyId = company.id();
        Map<Long, Long> userCounts = repository.countUsersByRole(companyId);
        return repository.findByCompanyId(companyId).stream()
                .map(role -> RoleRow.of(role, userCounts.getOrDefault(role.id(), 0L)))
                .toList();
    }

    @Override
    public RoleRow get(long id) {
        Role role = requireRole(id);
        return RoleRow.of(role, repository.countUsersByRole(company.id()).getOrDefault(id, 0L));
    }

    @Override
    public RoleRow create(CreateRoleRequest request) {
        long companyId = company.id();
        RoleValidator.validateCreate(request.permissions(), repository.countCustomByCompanyId(companyId));
        requireNameFree(companyId, request.name(), null);
        Role created = repository.create(companyId,
                Role.custom(request.name(), request.description(), request.permissions()));
        audit.record("ROLE_CREATE", "role", String.valueOf(created.id()), created.name());
        return RoleRow.of(created, 0L);
    }

    @Override
    public RoleRow update(long id, UpdateRoleRequest request) {
        long companyId = company.id();
        Role existing = requireRole(id);
        RoleValidator.validateEditable(existing);
        RoleValidator.validatePermissions(request.permissions());
        requireNameFree(companyId, request.name(), id);
        Role updated = repository.update(companyId,
                new Role(id, companyId, null, request.name(), request.description(), false,
                        request.permissions(), existing.createdAt()));
        // Permissions travel inside the access token, so a token issued a minute ago still
        // carries the old set — the only honest way to apply a narrowed role immediately is
        // to make its holders log in again.
        revokeSessions(companyId, id);
        audit.record("ROLE_UPDATE", "role", String.valueOf(id), updated.name());
        return RoleRow.of(updated, repository.countUsersByRole(companyId).getOrDefault(id, 0L));
    }

    @Override
    public void delete(long id) {
        long companyId = company.id();
        Role existing = requireRole(id);
        RoleValidator.validateEditable(existing);
        RoleValidator.validateNotInUse(existing, repository.countUsersByRole(companyId).getOrDefault(id, 0L));
        repository.delete(companyId, id);
        audit.record("ROLE_DELETE", "role", String.valueOf(id), existing.name());
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
                groups.add(new PermissionGroupRow(group, permissions));
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
    public void createSystemRoles(long companyId) {
        for (SystemRole systemRole : SystemRole.values()) {
            if (systemRole == SystemRole.SUPERADMIN && companyId != companyProperties.defaultId()) {
                // Platform staff live in the platform's own tenant; every other company
                // would otherwise be able to see a role that reaches across tenants.
                continue;
            }
            if (repository.findByCode(companyId, systemRole.name()) == null) {
                repository.create(companyId, Role.ofSystemRole(systemRole));
            }
        }
    }

    private Role requireRole(long id) {
        Role role = repository.find(company.id(), id);
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
        repository.findUserIdsByRoleId(companyId, roleId).forEach(sessionUseCase::revokeAllForUser);
    }
}
