package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeSourceEntity;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "source_id", insertable = false, updatable = false)
    private Long sourceId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Big-endian float32s — see {@code KnowledgeChunkRepositoryAdapter} for the layout. */
    @Column(name = "embedding")
    private byte[] embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public KnowledgeSourceEntity getSource() {
        return source;
    }

    public void setSource(KnowledgeSourceEntity source) {
        this.source = source;
    }

    public long getSourceId() {
        return source != null ? source.getId() : 0L;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
