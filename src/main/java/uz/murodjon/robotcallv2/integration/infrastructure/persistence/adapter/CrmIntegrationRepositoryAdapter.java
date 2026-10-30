package uz.murodjon.robotcallv2.integration.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmIntegrationRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CrmIntegrationEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.mapper.CrmIntegrationEntityMapper;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository.CrmIntegrationJpaRepository;

import java.time.Instant;
import java.util.Optional;

@Component
public class CrmIntegrationRepositoryAdapter implements CrmIntegrationRepository {

    private final CrmIntegrationJpaRepository jpaRepository;
    private final CrmIntegrationEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public CrmIntegrationRepositoryAdapter(CrmIntegrationJpaRepository jpaRepository, CrmIntegrationEntityMapper mapper,
                                           CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public Optional<CrmIntegration> find(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(mapper::toCrmIntegration);
    }

    @Override
    public CrmIntegration saveAppInfo(long companyId, String appName, String grantsJson) {
        CrmIntegrationEntity entity = findOrCreate(companyId);
        // Saving Uysot's app info is also how a company comes back to Uysot from another CRM.
        entity.setProvider(CrmProvider.UYSOT);
        entity.setAppName(appName);
        entity.setGrantsJson(grantsJson);
        entity.setAccessTokenEnc(null);
        entity.setRefreshTokenEnc(null);
        entity.setBaseUrlEnc(null);
        entity.setTokenExpiresAt(null);
        entity.setConnectedAt(null);
        entity.setStatus(CrmIntegrationStatus.NOT_CONNECTED);
        return mapper.toCrmIntegration(jpaRepository.save(entity));
    }

    @Override
    public CrmIntegration saveCredentials(long companyId, CrmIntegration credentials) {
        CrmIntegrationEntity entity = findOrCreate(companyId);
        entity.setProvider(credentials.provider());
        // Uysot's app name and grants describe a consent this provider does not have.
        entity.setAppName(null);
        entity.setGrantsJson(null);
        entity.setAccessTokenEnc(credentials.accessTokenEnc());
        entity.setRefreshTokenEnc(null);
        entity.setBaseUrlEnc(credentials.baseUrlEnc());
        entity.setTokenExpiresAt(null);
        entity.setStatus(credentials.status());
        entity.setConnectedAt(Instant.now());
        return mapper.toCrmIntegration(jpaRepository.save(entity));
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
            return mapper.toCrmIntegration(jpaRepository.save(entity));
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
    public void markConnected(long companyId) {
        jpaRepository.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setStatus(CrmIntegrationStatus.CONNECTED);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void disconnect(long companyId) {
        jpaRepository.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setAccessTokenEnc(null);
            entity.setRefreshTokenEnc(null);
            entity.setBaseUrlEnc(null);
            entity.setTokenExpiresAt(null);
            entity.setConnectedAt(null);
            entity.setStatus(CrmIntegrationStatus.NOT_CONNECTED);
            jpaRepository.save(entity);
        });
    }

    private CrmIntegrationEntity findOrCreate(long companyId) {
        return jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            CrmIntegrationEntity fresh = new CrmIntegrationEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
    }
}
