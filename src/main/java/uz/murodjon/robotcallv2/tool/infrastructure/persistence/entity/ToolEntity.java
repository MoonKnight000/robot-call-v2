package uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Entity
@Table(name = "tool")
public class ToolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "api_url", nullable = false, length = 1000)
    private String apiUrl;

    @Column(name = "api_method", nullable = false, length = 10)
    private String apiMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_headers", columnDefinition = "jsonb")
    private String apiHeaders;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_body", columnDefinition = "jsonb")
    private String apiBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_query_params", columnDefinition = "jsonb")
    private String apiQueryParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_path_params", columnDefinition = "jsonb")
    private String apiPathParams;

    @Column(name = "response_timeout_secs")
    private Integer responseTimeoutSecs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dynamic_variables", columnDefinition = "jsonb")
    private String dynamicVariables;

    @Column(name = "disable_interruptions", nullable = false)
    private boolean disableInterruptions;

    @Column(name = "force_pre_tool_speech", nullable = false)
    private boolean forcePreToolSpeech;

    @Column(name = "pre_tool_speech", length = 500)
    private String preToolSpeech;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiMethod() {
        return apiMethod;
    }

    public void setApiMethod(String apiMethod) {
        this.apiMethod = apiMethod;
    }

    public String getApiHeaders() {
        return apiHeaders;
    }

    public void setApiHeaders(String apiHeaders) {
        this.apiHeaders = apiHeaders;
    }

    public String getApiBody() {
        return apiBody;
    }

    public void setApiBody(String apiBody) {
        this.apiBody = apiBody;
    }

    public String getApiQueryParams() {
        return apiQueryParams;
    }

    public void setApiQueryParams(String apiQueryParams) {
        this.apiQueryParams = apiQueryParams;
    }

    public String getApiPathParams() {
        return apiPathParams;
    }

    public void setApiPathParams(String apiPathParams) {
        this.apiPathParams = apiPathParams;
    }

    public Integer getResponseTimeoutSecs() {
        return responseTimeoutSecs;
    }

    public void setResponseTimeoutSecs(Integer responseTimeoutSecs) {
        this.responseTimeoutSecs = responseTimeoutSecs;
    }

    public String getDynamicVariables() {
        return dynamicVariables;
    }

    public void setDynamicVariables(String dynamicVariables) {
        this.dynamicVariables = dynamicVariables;
    }

    public boolean isDisableInterruptions() {
        return disableInterruptions;
    }

    public void setDisableInterruptions(boolean disableInterruptions) {
        this.disableInterruptions = disableInterruptions;
    }

    public boolean isForcePreToolSpeech() {
        return forcePreToolSpeech;
    }

    public void setForcePreToolSpeech(boolean forcePreToolSpeech) {
        this.forcePreToolSpeech = forcePreToolSpeech;
    }

    public String getPreToolSpeech() {
        return preToolSpeech;
    }

    public void setPreToolSpeech(String preToolSpeech) {
        this.preToolSpeech = preToolSpeech;
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
