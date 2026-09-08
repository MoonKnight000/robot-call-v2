package uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.time.Instant;

/**
 * JPA entity for crm_integration (§11 settings).
 */
@Entity
@Table(name = "crm_integration")
public class CrmIntegrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrmProvider provider = CrmProvider.UYSOT;

    @Column(name = "app_name")
    private String appName;

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

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
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
