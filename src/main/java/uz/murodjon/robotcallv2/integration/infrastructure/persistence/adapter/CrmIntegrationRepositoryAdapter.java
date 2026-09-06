package uz.murodjon.robotcallv2.integration.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.integration.application.mapper.CrmIntegrationMapper;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmIntegrationRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CrmIntegrationEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository.CrmIntegrationJpaRepository;

import java.time.Instant;
import java.util.Optional;

@Component
public class CrmIntegrationRepositoryAdapter implements CrmIntegrationRepository {

    private final CrmIntegrationJpaRepository jpaRepository;
    private final CrmIntegrationMapper mapper;

    public CrmIntegrationRepositoryAdapter(CrmIntegrationJpaRepository jpaRepository, CrmIntegrationMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<CrmIntegration> find(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(mapper::entityToDomain);
    }

    @Override
    public CrmIntegration saveAppInfo(long companyId, String appName, String grantsJson) {
        CrmIntegrationEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            CrmIntegrationEntity fresh = new CrmIntegrationEntity();
            fresh.setCompanyId(companyId);
            fresh.setProvider(CrmProvider.UYSOT);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setAppName(appName);
        entity.setGrantsJson(grantsJson);
        entity.setAccessTokenEnc(null);
        entity.setRefreshTokenEnc(null);
        entity.setTokenExpiresAt(null);
        entity.setConnectedAt(null);
        entity.setStatus(CrmIntegrationStatus.NOT_CONNECTED);
        return mapper.entityToDomain(jpaRepository.save(entity));
    }

    @Override
    public CrmIntegration applyTokenResponse(long companyId, String accessTokenEnc, String refreshTokenEnc,
                                              Instant tokenExpiresAt) {
        return jpaRepository.findByCompanyId(companyId).map(entity -> {
            entity.setAccessTokenEnc(accessTokenEnc);
            if (refreshTokenEnc != null) {
                entity.setRefreshTokenEnc(refreshTokenEnc);
            }
            entity.setTokenExpiresAt(tokenExpiresAt);
            entity.setStatus(CrmIntegrationStatus.CONNECTED);
            entity.setConnectedAt(Instant.now());
            return mapper.entityToDomain(jpaRepository.save(entity));
        }).orElse(null);
    }

    @Override
    public void markError(long companyId) {
        jpaRepository.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setStatus(CrmIntegrationStatus.ERROR);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void disconnect(long companyId) {
        jpaRepository.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setAccessTokenEnc(null);
            entity.setRefreshTokenEnc(null);
            entity.setTokenExpiresAt(null);
            entity.setConnectedAt(null);
            entity.setStatus(CrmIntegrationStatus.NOT_CONNECTED);
            jpaRepository.save(entity);
        });
    }
}
