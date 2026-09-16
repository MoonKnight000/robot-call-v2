package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleAction;

import java.time.Instant;

/** JPA entity for inbound_caller_rule. */
@Entity
@Table(name = "inbound_caller_rule")
public class InboundCallerRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "phone_prefix", nullable = false)
    private String phonePrefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallerRuleAction action;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getPhonePrefix() {
        return phonePrefix;
    }

    public void setPhonePrefix(String phonePrefix) {
        this.phonePrefix = phonePrefix;
    }

    public CallerRuleAction getAction() {
        return action;
    }

    public void setAction(CallerRuleAction action) {
        this.action = action;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
