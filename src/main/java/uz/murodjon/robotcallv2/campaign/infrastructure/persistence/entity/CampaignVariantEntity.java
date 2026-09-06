package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "campaign_variant")
public class CampaignVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "ai_agent_id")
    private Long aiAgentId;

    @Column(name = "prompt_override", columnDefinition = "TEXT")
    private String promptOverride;

    @Column(name = "tts_voice_id", length = 64)
    private String ttsVoiceId;

    @Column(name = "traffic_weight", nullable = false)
    private int trafficWeight = 50;

    @Column(name = "calls_count", nullable = false)
    private int callsCount = 0;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount = 0;

    @Column(name = "converted_count", nullable = false)
    private int convertedCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CampaignVariantEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAiAgentId() {
        return aiAgentId;
    }

    public void setAiAgentId(Long aiAgentId) {
        this.aiAgentId = aiAgentId;
    }

    public String getPromptOverride() {
        return promptOverride;
    }

    public void setPromptOverride(String promptOverride) {
        this.promptOverride = promptOverride;
    }

    public String getTtsVoiceId() {
        return ttsVoiceId;
    }

    public void setTtsVoiceId(String ttsVoiceId) {
        this.ttsVoiceId = ttsVoiceId;
    }

    public int getTrafficWeight() {
        return trafficWeight;
    }

    public void setTrafficWeight(int trafficWeight) {
        this.trafficWeight = trafficWeight;
    }

    public int getCallsCount() {
        return callsCount;
    }

    public void setCallsCount(int callsCount) {
        this.callsCount = callsCount;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public void setAnsweredCount(int answeredCount) {
        this.answeredCount = answeredCount;
    }

    public int getConvertedCount() {
        return convertedCount;
    }

    public void setConvertedCount(int convertedCount) {
        this.convertedCount = convertedCount;
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
