package uz.murodjon.robotcallv2.role.application.port.output;

import uz.murodjon.robotcallv2.role.domain.entity.Role;

import java.util.List;
import java.util.Map;

public interface RoleRepository {

    List<Role> findByCompanyId(long companyId);

    Role find(long companyId, long id);

    Role findByCode(long companyId, String code);

    boolean existsByName(long companyId, String name, Long excludeId);

    long countCustomByCompanyId(long companyId);

    Role create(long companyId, Role role);

    Role update(long companyId, Role role);

    void delete(long companyId, long id);

    /** How many users hold each role of the company, keyed by role id. */
    Map<Long, Long> countUsersByRole(long companyId);

    List<Long> findUserIdsByRoleId(long companyId, long roleId);
}
