package uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;

import java.time.Instant;

/** JPA entity for integration_tool: the catalog of tools an integration offers the agent. */
@Entity
@Table(name = "integration_tool")
public class IntegrationToolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrmProvider provider;

    @Column(name = "tool_key", nullable = false)
    private String toolKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationToolKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_group", nullable = false)
    private CrmToolGroup toolGroup;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    private CrmPermission permission;

    @Enumerated(EnumType.STRING)
    private CrmScopeLevel scope;

    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;

    @Column(name = "platform_enabled", nullable = false)
    private boolean platformEnabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** The definition of an HTTP tool as JSON text; null for a built-in one. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String definition;

    /** What the model reads about a built-in tool; null = the text in code. */
    @Column(name = "model_description")
    private String modelDescription;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CrmProvider getProvider() {
        return provider;
    }

    public void setProvider(CrmProvider provider) {
        this.provider = provider;
    }

    public String getToolKey() {
        return toolKey;
    }

    public void setToolKey(String toolKey) {
        this.toolKey = toolKey;
    }

    public IntegrationToolKind getKind() {
        return kind;
    }

    public void setKind(IntegrationToolKind kind) {
        this.kind = kind;
    }

    public CrmToolGroup getToolGroup() {
        return toolGroup;
    }

    public void setToolGroup(CrmToolGroup toolGroup) {
        this.toolGroup = toolGroup;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CrmPermission getPermission() {
        return permission;
    }

    public void setPermission(CrmPermission permission) {
        this.permission = permission;
    }

    public CrmScopeLevel getScope() {
        return scope;
    }

    public void setScope(CrmScopeLevel scope) {
        this.scope = scope;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean isPlatformEnabled() {
        return platformEnabled;
    }

    public void setPlatformEnabled(boolean platformEnabled) {
        this.platformEnabled = platformEnabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getModelDescription() {
        return modelDescription;
    }

    public void setModelDescription(String modelDescription) {
        this.modelDescription = modelDescription;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
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
