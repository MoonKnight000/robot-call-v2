package uz.murodjon.robotcallv2.role.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.role.application.port.output.RoleTemplateRepository;
import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleTemplateEntity;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.mapper.RoleTemplateEntityMapper;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.repository.RoleTemplateJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class RoleTemplateRepositoryAdapter implements RoleTemplateRepository {

    private final RoleTemplateJpaRepository jpaRepository;
    private final RoleTemplateEntityMapper mapper;

    public RoleTemplateRepositoryAdapter(RoleTemplateJpaRepository jpaRepository, RoleTemplateEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<RoleTemplate> findAll() {
        return jpaRepository.findAllByOrderById().stream().map(mapper::toRoleTemplate).toList();
    }

    @Override
    public RoleTemplate find(long id) {
        return jpaRepository.findById(id).map(mapper::toRoleTemplate).orElse(null);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return jpaRepository.existsByName(name, excludeId);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public RoleTemplate create(RoleTemplate template) {
        RoleTemplateEntity entity = new RoleTemplateEntity();
        entity.setCode(template.code());
        entity.setName(template.name());
        entity.setDescription(template.description());
        entity.setAssignableByCompany(template.assignableByCompany());
        entity.setPermissions(template.permissions());
        entity.setCreatedAt(Instant.now());
        return mapper.toRoleTemplate(jpaRepository.save(entity));
    }

    @Override
    public RoleTemplate update(RoleTemplate template) {
        RoleTemplateEntity entity = jpaRepository.findById(template.id()).orElse(null);
        if (entity == null) {
            return null;
        }
        entity.setName(template.name());
        entity.setDescription(template.description());
        entity.setAssignableByCompany(template.assignableByCompany());
        entity.setPermissions(template.permissions());
        return mapper.toRoleTemplate(jpaRepository.save(entity));
    }

    @Override
    public void delete(long id) {
        jpaRepository.deleteById(id);
    }
}
