package uz.murodjon.uysotvoice.integration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;

import java.time.Instant;

/**
 * JPA entity for {@code crm_integration} (§11 settings) — one company's Uysot CRM OAuth
 * connection. {@code accessTokenEnc}/{@code refreshTokenEnc} are {@code
 * shared.util.SecretCipher} ciphertext, never plaintext.
 *
 * <p>{@code appName}/{@code grantsJson} (report #10) — not {@code clientId}/{@code
 * clientSecret}: per Uysot's real OAuth docs, one platform-wide app (a single {@code
 * client_id}/{@code client_secret}, {@code integration.config.UysotOAuthProperties})
 * serves every company — which company is connecting is decided by who is logged into
 * Uysot during consent, not by a per-company client id. Each company only supplies its
 * own {@code app_name} (shown on Uysot's consent screen) and which {@code grants}
 * (scopes) it wants to request.
 */
@Entity
@Table(name = "crm_integration")
public class CrmIntegrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrmProvider provider = CrmProvider.UYSOT;

    @Column(name = "app_name")
    private String appName;

    /** JSON array of {@code {"permission":"LEAD","scope":"READ"}} — see {@code CrmGrant}. */
    @Column(name = "grants_json")
    private String grantsJson;

    @Column(name = "access_token_enc")
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc")
    private String refreshTokenEnc;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrmIntegrationStatus status = CrmIntegrationStatus.NOT_CONNECTED;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public CrmProvider getProvider() {
        return provider;
    }

    public void setProvider(CrmProvider provider) {
        this.provider = provider;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getGrantsJson() {
        return grantsJson;
    }

    public void setGrantsJson(String grantsJson) {
        this.grantsJson = grantsJson;
    }

    public String getAccessTokenEnc() {
        return accessTokenEnc;
    }

    public void setAccessTokenEnc(String accessTokenEnc) {
        this.accessTokenEnc = accessTokenEnc;
    }

    public String getRefreshTokenEnc() {
        return refreshTokenEnc;
    }

    public void setRefreshTokenEnc(String refreshTokenEnc) {
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Instant tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public CrmIntegrationStatus getStatus() {
        return status;
    }

    public void setStatus(CrmIntegrationStatus status) {
        this.status = status;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
