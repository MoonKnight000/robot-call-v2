package uz.murodjon.uysotvoice.donotcall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;

import java.time.Instant;

/** JPA entity for {@code do_not_call_list} — the phone-level opt-out list (PROJECT.md §11.4). */
@Entity
@Table(name = "do_not_call_list")
public class DoNotCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phone;

    @Column
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoNotCallSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private String removedBy;

    public Long getId() {
        return id;
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

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
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
}
