package uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_totp")
public class UserTotpEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLastUsedStep() {
        return lastUsedStep;
    }

    public void setLastUsedStep(long lastUsedStep) {
        this.lastUsedStep = lastUsedStep;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEnabledAt() {
        return enabledAt;
    }

    public void setEnabledAt(Instant enabledAt) {
        this.enabledAt = enabledAt;
    }
}
