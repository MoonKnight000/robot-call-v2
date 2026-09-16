package uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "telegram_bot")
public class TelegramBotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ai_agent_id", nullable = false)
    private Long aiAgentId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "token_secret_key", nullable = false, length = 64)
    private String tokenSecretKey;

    @Column(name = "webhook_secret", nullable = false, length = 64)
    private String webhookSecret;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getAiAgentId() { return aiAgentId; }

    public void setAiAgentId(Long aiAgentId) { this.aiAgentId = aiAgentId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getTokenSecretKey() { return tokenSecretKey; }

    public void setTokenSecretKey(String tokenSecretKey) { this.tokenSecretKey = tokenSecretKey; }

    public String getWebhookSecret() { return webhookSecret; }

    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getLanguage() { return language; }

    public void setLanguage(String language) { this.language = language; }

    public String getWebhookUrl() { return webhookUrl; }

    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
