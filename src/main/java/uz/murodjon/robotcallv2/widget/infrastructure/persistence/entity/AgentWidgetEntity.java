package uz.murodjon.robotcallv2.widget.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Entity
@Table(name = "agent_widget")
public class AgentWidgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "widget_key", nullable = false, unique = true, length = 64)
    private String widgetKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private AiAgentEntity agent;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "agent_id", insertable = false, updatable = false)
    private Long agentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_origins", columnDefinition = "jsonb")
    private String allowedOrigins;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme", columnDefinition = "jsonb")
    private String theme;

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired = true;

    @Column(name = "consent_text", length = 500)
    private String consentText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWidgetKey() {
        return widgetKey;
    }

    public void setWidgetKey(String widgetKey) {
        this.widgetKey = widgetKey;
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

    public AiAgentEntity getAgent() {
        return agent;
    }

    public void setAgent(AiAgentEntity agent) {
        this.agent = agent;
    }

    public long getAgentId() {
        return agent != null ? agent.getId() : 0L;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isConsentRequired() {
        return consentRequired;
    }

    public void setConsentRequired(boolean consentRequired) {
        this.consentRequired = consentRequired;
    }

    public String getConsentText() {
        return consentText;
    }

    public void setConsentText(String consentText) {
        this.consentText = consentText;
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
