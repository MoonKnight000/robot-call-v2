package uz.murodjon.robotcallv2.role.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.role.application.mapper.RoleMapper;
import uz.murodjon.robotcallv2.role.application.port.output.RoleRepository;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.repository.RoleJpaRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;
    private final RoleMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public RoleRepositoryAdapter(RoleJpaRepository jpaRepository, RoleMapper mapper, JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
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
        entity.setCompanyId(companyId);
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

    @Override
    public Map<Long, Long> countUsersByRole(long companyId) {
        Map<Long, Long> counts = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT role_id, COUNT(*) AS user_count FROM app_user WHERE company_id = ? GROUP BY role_id",
                companyId);
        for (Map<String, Object> row : rows) {
            counts.put(((Number) row.get("role_id")).longValue(), ((Number) row.get("user_count")).longValue());
        }
        return counts;
    }

    @Override
    public List<Long> findUserIdsByRoleId(long companyId, long roleId) {
        return jdbcTemplate.queryForList("SELECT id FROM app_user WHERE company_id = ? AND role_id = ?",
                Long.class, companyId, roleId);
    }
}
