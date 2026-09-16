package uz.murodjon.robotcallv2.role.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.mapper.RoleEntityMapper;
import uz.murodjon.robotcallv2.role.application.port.output.RoleRepository;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.repository.RoleJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;
    private final RoleEntityMapper mapper;
    private final UserJpaRepository userJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;

    public RoleRepositoryAdapter(RoleJpaRepository jpaRepository, RoleEntityMapper mapper,
                                 UserJpaRepository userJpaRepository, CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.userJpaRepository = userJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public List<Role> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdOrderById(companyId).stream().map(mapper::toRole).toList();
    }

    @Override
    public Role find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toRole).orElse(null);
    }

    @Override
    public Role findByCode(long companyId, String code) {
        return jpaRepository.findByCompanyIdAndCode(companyId, code).map(mapper::toRole).orElse(null);
    }

    @Override
    public boolean existsByName(long companyId, String name, Long excludeId) {
        return jpaRepository.existsByName(companyId, name, excludeId);
    }

    @Override
    public long countCustomByCompanyId(long companyId) {
        return jpaRepository.countByCompanyIdAndSystem(companyId, false);
    }

    @Override
    public Role create(long companyId, Role role) {
        RoleEntity entity = new RoleEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setCode(role.code());
        entity.setName(role.name());
        entity.setDescription(role.description());
        entity.setSystem(role.system());
        // A system role's permissions are computed from its code on every read, so storing
        // them would only create a second truth that can drift.
        entity.setPermissions(role.system() ? new LinkedHashSet<>() : new LinkedHashSet<>(role.permissions()));
        entity.setCreatedAt(Instant.now());
        return mapper.toRole(jpaRepository.save(entity));
    }

    @Override
    public Role update(long companyId, Role role) {
        RoleEntity entity = jpaRepository.findByIdAndCompanyId(role.id(), companyId).orElse(null);
        if (entity == null) {
            return null;
        }
        entity.setName(role.name());
        entity.setDescription(role.description());
        entity.getPermissions().clear();
        entity.getPermissions().addAll(role.permissions());
        return mapper.toRole(jpaRepository.save(entity));
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    /*
     * app_user belongs to the user feature, so these two ask its JPA repository in JPQL
     * rather than SQL against the table. Not through UserUseCase: UserService already
     * depends on RoleUseCase, and the reverse would be a bean cycle.
     */

    @Override
    public Map<Long, Long> countUsersByRole(long companyId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : userJpaRepository.countByCompanyIdGroupedByRoleId(companyId)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    public List<Long> findUserIdsByRoleId(long companyId, long roleId) {
        return userJpaRepository.findIdsByCompanyIdAndRoleId(companyId, roleId);
    }
}
