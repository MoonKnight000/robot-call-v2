package uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** JPA entity for contact_crm_link. */
@Entity
@Table(name = "contact_crm_link")
public class ContactCrmLinkEntity {

    @Id
    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "push_requested_at")
    private Instant pushRequestedAt;

    @Column(name = "push_next_at")
    private Instant pushNextAt;

    @Column(name = "push_attempts", nullable = false)
    private int pushAttempts;

    @Column(name = "push_error", length = 500)
    private String pushError;

    @Column(name = "pushed_at")
    private Instant pushedAt;

    public Instant getPushRequestedAt() {
        return pushRequestedAt;
    }

    public int getPushAttempts() {
        return pushAttempts;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}
