package uz.murodjon.uysotvoice.integration.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.integration.domain.CrmIntegration;
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

    public Optional<CrmIntegration> find(long companyId) {
        return jpa.findByCompanyId(companyId).map(CrmIntegrationRepository::toCrmIntegration);
    }

    /** Upsert the app identity/grants; resets any prior connection — new grants need a fresh OAuth dance. */
    public CrmIntegration saveAppInfo(long companyId, String appName, String grantsJson) {
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
        return toCrmIntegration(jpa.save(entity));
    }

    /**
     * Stores a fresh access/refresh token pair and marks the row {@code CONNECTED}.
     * {@code refreshTokenEnc} of {@code null} leaves the existing refresh token
     * untouched — Uysot's refresh response does not always include a new one.
     * {@code null} if {@code companyId} has no row (should not happen: every caller
     * has just loaded it via {@link #find}).
     */
    public CrmIntegration applyTokenResponse(long companyId, String accessTokenEnc, String refreshTokenEnc,
                                              Instant tokenExpiresAt) {
        return jpa.findByCompanyId(companyId).map(entity -> {
            entity.setAccessTokenEnc(accessTokenEnc);
            if (refreshTokenEnc != null) {
                entity.setRefreshTokenEnc(refreshTokenEnc);
            }
            entity.setTokenExpiresAt(tokenExpiresAt);
            entity.setStatus(CrmIntegrationStatus.CONNECTED);
            entity.setConnectedAt(Instant.now());
            return toCrmIntegration(jpa.save(entity));
        }).orElse(null);
    }

    /** A token exchange/refresh failed upstream — the connection needs re-authorizing. */
    public void markError(long companyId) {
        jpa.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setStatus(CrmIntegrationStatus.ERROR);
            jpa.save(entity);
        });
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

    private static CrmIntegration toCrmIntegration(CrmIntegrationEntity e) {
        return new CrmIntegration(e.getCompanyId(), e.getProvider(), e.getAppName(), e.getGrantsJson(),
                e.getAccessTokenEnc(), e.getRefreshTokenEnc(), e.getTokenExpiresAt(), e.getStatus(),
                e.getConnectedAt(), e.getCreatedAt());
    }
}
