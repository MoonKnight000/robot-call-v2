package uz.murodjon.robotcallv2.role.domain.service;

import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionScope;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The rules a role has to satisfy, independent of storage or HTTP. */
public final class RoleValidator {

    /** All roles of one company together — the ones copied from templates count too. */
    public static final int MAX_ROLES = 15;

    private RoleValidator() {
    }

    public static void validateCreate(Set<Permission> permissions, long existingRoles) {
        if (existingRoles >= MAX_ROLES) {
            throw new ConflictException(ErrorCode.ROLE_LIMIT_EXCEEDED, MAX_ROLES);
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

    /**
     * Any company role may be edited or deleted, except one only platform staff may hand out
     * (DEVELOPER): a tenant admin could otherwise strip the platform engineer's access.
     */
    public static void validateEditable(Role role, boolean platformAdmin) {
        if (!role.assignableByCompany() && !platformAdmin) {
            throw new ForbiddenException(ErrorCode.ROLE_PLATFORM_MANAGED, role.name());
        }
    }

    /**
     * Taking USER_EDIT away from a role must leave someone active who can still manage users;
     * a company that locks itself out can only be recovered from the database.
     */
    public static void validateKeepsUserManager(Role role, Set<Permission> newPermissions,
                                                long activeHolders, long otherActiveManagers) {
        boolean dropsUserEdit = role.hasPermission(Permission.USER_EDIT) && !newPermissions.contains(Permission.USER_EDIT);
        if (dropsUserEdit && activeHolders > 0 && otherActiveManagers == 0) {
            throw new ConflictException(ErrorCode.ROLE_EDIT_LOCKS_OUT_ADMINS, role.name());
        }
    }

    public static void validateNotInUse(Role role, long assignedUsers) {
        if (assignedUsers > 0) {
            throw new ConflictException(ErrorCode.ROLE_IN_USE, role.name(), assignedUsers);
        }
    }

    /**
     * Nobody hands out a permission they do not hold themselves — not by assigning a role,
     * not by inviting someone into one, not by editing one. Without this, USER_EDIT made a
     * user their own ADMIN and ROLE_EDIT grew the caller's own role to every permission.
     */
    public static void validateGrantable(Set<Permission> permissions, Predicate<Permission> callerHolds) {
        permissions.stream()
                .filter(permission -> !callerHolds.test(permission))
                .findFirst()
                .ifPresent(permission -> {
                    throw new ForbiddenException(ErrorCode.ROLE_PERMISSION_NOT_HELD, permission.name());
                });
    }

    /**
     * The ADMIN template seeds the first administrator of every new company, so it can neither
     * disappear nor lose the rights to manage users and roles — otherwise that company could never
     * invite anyone else.
     */
    public static void validateTemplatePermissions(String code, Set<Permission> permissions) {
        validatePermissions(permissions);
        if (!SystemRole.ADMIN.name().equals(code)) {
            return;
        }
        for (Permission required : List.of(Permission.USER_EDIT, Permission.ROLE_EDIT)) {
            if (!permissions.contains(required)) {
                throw new ValidationException(ErrorCode.ROLE_TEMPLATE_ADMIN_PERMISSION_REQUIRED, required.name());
            }
        }
    }

    public static void validateTemplateDeletable(RoleTemplate template) {
        if (SystemRole.ADMIN.name().equals(template.code())) {
            throw new ConflictException(ErrorCode.ROLE_TEMPLATE_PROTECTED, template.name());
        }
    }

    /** DEVELOPER is handed out by platform staff only, never by a tenant admin. */
    public static void validateAssignable(Role role, boolean platformAdmin) {
        if (!role.assignableByCompany() && !platformAdmin) {
            throw new ForbiddenException(ErrorCode.ROLE_NOT_ASSIGNABLE, role.name());
        }
    }
}
