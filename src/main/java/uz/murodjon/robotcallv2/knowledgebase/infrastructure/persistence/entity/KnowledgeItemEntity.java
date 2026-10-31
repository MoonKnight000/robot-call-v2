package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Entity
@Table(name = "knowledge_base_item")
public class KnowledgeItemEntity {

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

    @Column(name = "item_key", nullable = false, length = 100)
    private String itemKey;

    @Column(name = "topic", nullable = false, length = 50)
    private String topic;

    @Column(name = "title", nullable = false)
    private String title;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_item_translation", joinColumns = @JoinColumn(name = "item_id"))
    @MapKeyColumn(name = "language", length = 10)
    @Column(name = "answer", columnDefinition = "TEXT", nullable = false)
    @org.hibernate.annotations.BatchSize(size = 50)
    private java.util.Map<String, String> answers = new java.util.LinkedHashMap<>();

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

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public Long getCompanyId() {
        return company != null ? company.getId() : null;
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
        return answers.get("uz");
    }

    public void setAnswerUz(String answerUz) {
        if (answerUz != null && !answerUz.isBlank()) {
            answers.put("uz", answerUz);
        } else {
            answers.remove("uz");
        }
    }

    public String getAnswerRu() {
        return answers.get("ru");
    }

    public void setAnswerRu(String answerRu) {
        if (answerRu != null && !answerRu.isBlank()) {
            answers.put("ru", answerRu);
        } else {
            answers.remove("ru");
        }
    }

    public String getAnswerEn() {
        return answers.get("en");
    }

    public void setAnswerEn(String answerEn) {
        if (answerEn != null && !answerEn.isBlank()) {
            answers.put("en", answerEn);
        } else {
            answers.remove("en");
        }
    }

    public java.util.Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(java.util.Map<String, String> answers) {
        this.answers = answers != null ? new java.util.LinkedHashMap<>(answers) : new java.util.LinkedHashMap<>();
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
