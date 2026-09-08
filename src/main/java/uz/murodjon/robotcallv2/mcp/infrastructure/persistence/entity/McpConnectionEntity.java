package uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpAuthType;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpConnectionStatus;

import java.time.Instant;

@Entity
@Table(name = "mcp_connection")
public class McpConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 16)
    private McpAuthType authType;

    @Column(name = "secret_key", length = 128)
    private String secretKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private McpConnectionStatus status;

    @Column(name = "tool_count", nullable = false)
    private Integer toolCount;

    @Column(name = "usable_tool_count", nullable = false)
    private Integer usableToolCount;

    @Column(name = "allow_writes", nullable = false)
    private Boolean allowWrites;

    @Column(name = "tools_json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String toolsJson;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "refreshed_at")
    private Instant refreshedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public McpAuthType getAuthType() { return authType; }
    public void setAuthType(McpAuthType authType) { this.authType = authType; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public McpConnectionStatus getStatus() { return status; }
    public void setStatus(McpConnectionStatus status) { this.status = status; }
    public Integer getToolCount() { return toolCount; }
    public void setToolCount(Integer toolCount) { this.toolCount = toolCount; }
    public Integer getUsableToolCount() { return usableToolCount; }
    public void setUsableToolCount(Integer usableToolCount) { this.usableToolCount = usableToolCount; }
    public Boolean getAllowWrites() { return allowWrites; }
    public void setAllowWrites(Boolean allowWrites) { this.allowWrites = allowWrites; }
    public String getToolsJson() { return toolsJson; }
    public void setToolsJson(String toolsJson) { this.toolsJson = toolsJson; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(Instant refreshedAt) { this.refreshedAt = refreshedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
