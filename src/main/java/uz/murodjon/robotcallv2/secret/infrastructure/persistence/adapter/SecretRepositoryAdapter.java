package uz.murodjon.robotcallv2.secret.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.secret.application.port.output.SecretRepository;
import uz.murodjon.robotcallv2.secret.domain.entity.Secret;
import uz.murodjon.robotcallv2.secret.infrastructure.persistence.entity.SecretEntity;
import uz.murodjon.robotcallv2.secret.infrastructure.persistence.mapper.SecretEntityMapper;
import uz.murodjon.robotcallv2.secret.infrastructure.persistence.repository.SecretJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class SecretRepositoryAdapter implements SecretRepository {

    private final SecretJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final SecretEntityMapper mapper;

    public SecretRepositoryAdapter(SecretJpaRepository jpaRepository,
                                   CompanyJpaRepository companyJpaRepository,
                                   SecretEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Secret save(Secret secret) {
        SecretEntity entity;
        if (secret.id() != null && secret.id() > 0) {
            entity = jpaRepository.findByCompanyIdAndId(secret.companyId(), secret.id())
                    .orElseGet(SecretEntity::new);
        } else {
            entity = new SecretEntity();
            entity.setCreatedAt(Instant.now());
        }
        entity.setCompany(companyJpaRepository.getReferenceById(secret.companyId()));
        entity.setKey(secret.key());
        entity.setValue(secret.value());
        entity.setDescription(secret.description());
        entity.setUpdatedAt(Instant.now());

        SecretEntity saved = jpaRepository.save(entity);
        return mapper.toSecret(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Secret> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toSecret);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Secret> findByCompanyId(long companyId) {
        return jpaRepository.findAllByCompanyIdOrderByKeyAsc(companyId)
                .stream()
                .map(mapper::toSecret)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCompanyIdAndKey(long companyId, String key) {
        return jpaRepository.existsByCompanyIdAndKey(companyId, key);
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }
}
