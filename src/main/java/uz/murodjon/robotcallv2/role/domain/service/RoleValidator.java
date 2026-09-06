package uz.murodjon.robotcallv2.role.domain.service;

import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionScope;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Set;

/** The rules a role has to satisfy, independent of storage or HTTP. */
public final class RoleValidator {

    /** A company defines its own roles on top of the seeded system ones; this caps those. */
    public static final int MAX_CUSTOM_ROLES = 10;

    private RoleValidator() {
    }

    public static void validateCreate(Set<Permission> permissions, long existingCustomRoles) {
        if (existingCustomRoles >= MAX_CUSTOM_ROLES) {
            throw new ConflictException(ErrorCode.ROLE_LIMIT_EXCEEDED, MAX_CUSTOM_ROLES);
        }
        validatePermissions(permissions);
    }

    public static void validatePermissions(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new ValidationException(ErrorCode.ROLE_PERMISSIONS_EMPTY);
        }
        permissions.stream()
                .filter(permission -> permission.scope() != PermissionScope.COMPANY)
                .findFirst()
                .ifPresent(permission -> {
                    throw new ValidationException(ErrorCode.ROLE_PERMISSION_NOT_GRANTABLE, permission.name());
                });
    }

    /** A system role's permissions come from {@link SystemRole}, so it cannot be edited or deleted. */
    public static void validateEditable(Role role) {
        if (role.system()) {
            throw new ConflictException(ErrorCode.ROLE_SYSTEM_READONLY, role.name());
        }
    }

    public static void validateNotInUse(Role role, long assignedUsers) {
        if (assignedUsers > 0) {
            throw new ConflictException(ErrorCode.ROLE_IN_USE, role.name(), assignedUsers);
        }
    }

    /** DEVELOPER and SUPERADMIN are handed out by platform staff only, never by a tenant admin. */
    public static void validateAssignable(Role role, boolean platformAdmin) {
        SystemRole systemRole = role.findSystemRole();
        if (systemRole != null && !systemRole.assignableByCompany() && !platformAdmin) {
            throw new ForbiddenException(ErrorCode.ROLE_NOT_ASSIGNABLE, role.name());
        }
    }
}
