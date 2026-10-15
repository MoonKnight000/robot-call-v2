package uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_client")
public class PartnerClientEntity {

    @Id
    @Column(name = "client_company_id")
    private Long clientCompanyId;

    @Column(name = "partner_company_id", nullable = false)
    private Long partnerCompanyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getClientCompanyId() {
        return clientCompanyId;
    }

    public void setClientCompanyId(Long clientCompanyId) {
        this.clientCompanyId = clientCompanyId;
    }

    public Long getPartnerCompanyId() {
        return partnerCompanyId;
    }

    public void setPartnerCompanyId(Long partnerCompanyId) {
        this.partnerCompanyId = partnerCompanyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
