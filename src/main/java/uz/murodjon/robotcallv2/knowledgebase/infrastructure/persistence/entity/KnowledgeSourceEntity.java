package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

import java.time.Instant;

@Entity
@Table(name = "knowledge_source")
public class KnowledgeSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private AiAgentEntity agent;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "agent_id", insertable = false, updatable = false)
    private Long agentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private KnowledgeSourceType sourceType;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stored_file_id")
    private StoredFileEntity storedFile;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeSourceStatus status = KnowledgeSourceStatus.PENDING;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

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

    public AiAgentEntity getAgent() {
        return agent;
    }

    public void setAgent(AiAgentEntity agent) {
        this.agent = agent;
    }

    public Long getAgentId() {
        return agent != null ? agent.getId() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KnowledgeSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(KnowledgeSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public StoredFileEntity getStoredFile() {
        return storedFile;
    }

    public void setStoredFile(StoredFileEntity storedFile) {
        this.storedFile = storedFile;
    }

    public Long getStoredFileId() {
        return storedFile != null ? storedFile.getId() : null;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public KnowledgeSourceStatus getStatus() {
        return status;
    }

    public void setStatus(KnowledgeSourceStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(Instant lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
