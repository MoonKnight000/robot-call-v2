package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "knowledge_base_item")
public class KnowledgeItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "item_key", nullable = false, length = 100)
    private String itemKey;

    @Column(name = "topic", nullable = false, length = 50)
    private String topic;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "answer_uz", nullable = false, columnDefinition = "TEXT")
    private String answerUz;

    @Column(name = "answer_ru", columnDefinition = "TEXT")
    private String answerRu;

    @Column(name = "answer_en", columnDefinition = "TEXT")
    private String answerEn;

    @Column(name = "keywords", nullable = false, columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public KnowledgeItemEntity() {
    }

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

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAnswerUz() {
        return answerUz;
    }

    public void setAnswerUz(String answerUz) {
        this.answerUz = answerUz;
    }

    public String getAnswerRu() {
        return answerRu;
    }

    public void setAnswerRu(String answerRu) {
        this.answerRu = answerRu;
    }

    public String getAnswerEn() {
        return answerEn;
    }

    public void setAnswerEn(String answerEn) {
        this.answerEn = answerEn;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
