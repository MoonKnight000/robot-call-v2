package uz.murodjon.robotcallv2.role.application.port.input;

import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.List;

public interface RoleUseCase {

    List<RoleRow> findByCompanyId(long companyId);

    RoleRow requireRoleRow(long companyId, long id);

    RoleRow create(long companyId, CreateRoleRequest request);

    RoleRow update(long companyId, long id, UpdateRoleRequest request);

    void delete(long companyId, long id);

    /** The full permission catalog, grouped by page — what the role editor renders. */
    List<PermissionGroupRow> listPermissions();

    /**
     * The role as stored, or {@code null} if it does not exist in that company. Takes the
     * company explicitly because login resolves a role before any company context exists.
     */
    Role findRole(long companyId, long roleId);

    /** The role copied from the template with this code (DEVELOPER, ADMIN, …), or {@code null} if the company has none. */
    Role findRoleByCode(long companyId, String code);

    /** Every role of the company that grants {@code permission} — e.g. "who can manage users". */
    List<Role> findRolesWithPermission(long companyId, Permission permission);

    /** Gives a company with no roles a copy of every platform template; a company that has roles is left alone. */
    void createRolesFromTemplates(long companyId);

    /** One-time upgrade: writes down the permissions of roles that were computed from {@code SystemRole}. */
    void materializeLegacySystemRoles();
}
