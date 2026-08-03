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
 * connection. {@code clientSecretEnc}/{@code accessTokenEnc}/{@code refreshTokenEnc} are
 * {@code shared.util.SecretCipher} ciphertext, never plaintext.
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

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret_enc")
    private String clientSecretEnc;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretEnc() {
        return clientSecretEnc;
    }

    public void setClientSecretEnc(String clientSecretEnc) {
        this.clientSecretEnc = clientSecretEnc;
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
