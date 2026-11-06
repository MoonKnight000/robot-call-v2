package uz.murodjon.robotcallv2.role.application.port.output;

import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RoleRepository {

    List<Role> findByCompanyId(long companyId);

    Role find(long companyId, long id);

    Role findByCode(long companyId, String code);

    boolean existsByName(long companyId, String name, Long excludeId);

    long countByCompanyId(long companyId);

    Role create(long companyId, Role role);

    Role update(long companyId, Role role);

    void delete(long companyId, long id);

    /** How many users hold each role of the company, keyed by role id. */
    Map<Long, Long> countUsersByRole(long companyId);

    List<Long> findUserIdsByRoleId(long companyId, long roleId);

    /** Active users holding any of these roles. */
    long countActiveUsersByRoleIds(long companyId, Collection<Long> roleIds);

    /**
     * Across every company: rows created before roles became editable, still marked as computed from
     * the {@code code} system role, get {@code permissions} written down and lose that mark. Returns
     * how many rows changed — zero once done.
     */
    int materializeLegacySystemRole(String code, Set<Permission> permissions);
}
