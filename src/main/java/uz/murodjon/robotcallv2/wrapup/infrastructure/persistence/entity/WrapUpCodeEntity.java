package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

import java.time.Instant;

/** JPA entity for wrap_up_code. */
@Entity
@Table(name = "wrap_up_code")
public class WrapUpCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WrapUpOutcome outcome;

    @Column(name = "requires_callback_at", nullable = false)
    private boolean requiresCallbackAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WrapUpOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(WrapUpOutcome outcome) {
        this.outcome = outcome;
    }

    public boolean isRequiresCallbackAt() {
        return requiresCallbackAt;
    }

    public void setRequiresCallbackAt(boolean requiresCallbackAt) {
        this.requiresCallbackAt = requiresCallbackAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
