package uz.murodjon.robotcallv2.operator.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

/** JPA entity for operator_sip_account — keyed by the user it belongs to. */
@Entity
@Table(name = "operator_sip_account")
public class OperatorSipAccountEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "endpoint_name", nullable = false)
    private String endpointName;

    @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT")
    private String passwordEnc;

    @Column(nullable = false)
    private boolean webrtc = true;

    @Column(name = "forward_number")
    private String forwardNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    public boolean isWebrtc() {
        return webrtc;
    }

    public void setWebrtc(boolean webrtc) {
        this.webrtc = webrtc;
    }

    public String getForwardNumber() {
        return forwardNumber;
    }

    public void setForwardNumber(String forwardNumber) {
        this.forwardNumber = forwardNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
