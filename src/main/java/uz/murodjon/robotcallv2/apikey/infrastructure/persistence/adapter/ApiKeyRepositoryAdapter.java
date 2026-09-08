package uz.murodjon.robotcallv2.apikey.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.apikey.application.mapper.ApiKeyMapper;
import uz.murodjon.robotcallv2.apikey.application.port.output.ApiKeyRepository;
import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;
import uz.murodjon.robotcallv2.apikey.infrastructure.persistence.entity.ApiKeyEntity;
import uz.murodjon.robotcallv2.apikey.infrastructure.persistence.repository.ApiKeyJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyRepositoryAdapter implements ApiKeyRepository {

    private final ApiKeyJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ApiKeyMapper mapper;

    public ApiKeyRepositoryAdapter(ApiKeyJpaRepository jpaRepository,
                                   CompanyJpaRepository companyJpaRepository,
                                   ApiKeyMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        CompanyEntity company = companyJpaRepository.getReferenceById(apiKey.companyId());
        return mapper.toApiKey(jpaRepository.save(mapper.toEntity(apiKey, company)));
    }

    @Override
    public List<ApiKey> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(mapper::toApiKey)
                .toList();
    }

    @Override
    public Optional<ApiKey> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toApiKey);
    }

    @Override
    public Optional<ApiKey> findByPrefix(String keyPrefix) {
        return jpaRepository.findByKeyPrefix(keyPrefix).map(mapper::toApiKey);
    }

    @Override
    @Transactional
    public void updateLastUsedAt(long id, Instant usedAt) {
        jpaRepository.updateLastUsedAt(id, usedAt);
    }

    @Override
    @Transactional
    public void updateRevokedAt(long companyId, long id, Instant revokedAt) {
        jpaRepository.updateRevokedAt(companyId, id, revokedAt);
    }
}
