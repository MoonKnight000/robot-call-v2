package uz.murodjon.uysotvoice.integration.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.integration.entity.CrmIntegrationEntity;
import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed DAO for {@code crm_integration} (§11 settings) — one row per company.
 * Takes an explicit {@code companyId} everywhere rather than depending on {@code
 * CurrentCompany}: {@code CrmClient}/the OAuth callback both resolve a specific
 * company's row outside the request-scoped settings-page context.
 */
@Repository
public class CrmIntegrationRepository {

    private final CrmIntegrationJpaRepository jpa;

    public CrmIntegrationRepository(CrmIntegrationJpaRepository jpa) {
        this.jpa = jpa;
    }

    public Optional<CrmIntegrationEntity> find(long companyId) {
        return jpa.findByCompanyId(companyId);
    }

    /** Upsert the app identity/grants; resets any prior connection — new grants need a fresh OAuth dance. */
    public CrmIntegrationEntity saveAppInfo(long companyId, String appName, String grantsJson) {
        CrmIntegrationEntity entity = jpa.findByCompanyId(companyId).orElseGet(() -> {
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
        return jpa.save(entity);
    }

    public CrmIntegrationEntity save(CrmIntegrationEntity entity) {
        return jpa.save(entity);
    }

    public void disconnect(long companyId) {
        jpa.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setAccessTokenEnc(null);
            entity.setRefreshTokenEnc(null);
            entity.setTokenExpiresAt(null);
            entity.setConnectedAt(null);
            entity.setStatus(CrmIntegrationStatus.NOT_CONNECTED);
            jpa.save(entity);
        });
    }
}
