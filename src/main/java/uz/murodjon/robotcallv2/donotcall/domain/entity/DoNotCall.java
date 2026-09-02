package uz.murodjon.robotcallv2.donotcall.domain.entity;

import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;

import java.time.Instant;

/**
 * Domain entity representing an entry in the Do-Not-Call (DNC) list.
 * Pure business model without framework dependencies.
 */
public class DoNotCall {
    private Long id;
    private String phone;
    private String reason;
    private DoNotCallSource source;
    private Instant createdAt;
    private Instant removedAt;
    private String removedBy;
    private Long companyId;

    public DoNotCall() {
    }

    public DoNotCall(Long id, String phone, String reason, DoNotCallSource source, Instant createdAt) {
        this.id = id;
        this.phone = phone;
        this.reason = reason;
        this.source = source;
        this.createdAt = createdAt;
    }

    public DoNotCall(Long id, String phone, String reason, DoNotCallSource source, Instant createdAt, Instant removedAt, String removedBy, Long companyId) {
        this.id = id;
        this.phone = phone;
        this.reason = reason;
        this.source = source;
        this.createdAt = createdAt;
        this.removedAt = removedAt;
        this.removedBy = removedBy;
        this.companyId = companyId;
    }

    public void remove(String removedBy) {
        this.removedAt = Instant.now();
        this.removedBy = removedBy;
    }

    public boolean isActive() {
        return this.removedAt == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public DoNotCallSource getSource() {
        return source;
    }

    public void setSource(DoNotCallSource source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public String getRemovedBy() {
        return removedBy;
    }

    public void setRemovedBy(String removedBy) {
        this.removedBy = removedBy;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }
}
