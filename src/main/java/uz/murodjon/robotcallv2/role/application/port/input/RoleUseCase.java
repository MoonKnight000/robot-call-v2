package uz.murodjon.robotcallv2.role.application.port.input;

import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.List;

public interface RoleUseCase {

    List<RoleRow> listForCurrentCompany();

    RoleRow get(long id);

    RoleRow create(CreateRoleRequest request);

    RoleRow update(long id, UpdateRoleRequest request);

    void delete(long id);

    /** The full permission catalog, grouped by page — what the role editor renders. */
    List<PermissionGroupRow> listPermissions();

    /**
     * The role as stored, or {@code null} if it does not exist in that company. Takes the
     * company explicitly because login resolves a role before any company context exists.
     */
    Role findRole(long companyId, long roleId);

    /** A system role by its code (DEVELOPER, ADMIN, …), or {@code null} if the company has none. */
    Role findRoleByCode(long companyId, String code);

    /** Every role of the company that grants {@code permission} — e.g. "who can manage users". */
    List<Role> findRolesWithPermission(long companyId, Permission permission);

    /** Seeds the system roles a company cannot exist without; safe to call repeatedly. */
    void createSystemRoles(long companyId);
}
