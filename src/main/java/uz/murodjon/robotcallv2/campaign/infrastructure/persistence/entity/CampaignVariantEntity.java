package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;

@Entity
@Table(name = "campaign_variant")
public class CampaignVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CampaignEntity campaign;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "campaign_id", insertable = false, updatable = false)
    private Long campaignId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id")
    private AiAgentEntity aiAgent;

    @Column(name = "prompt_override", columnDefinition = "TEXT")
    private String promptOverride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tts_voice_id")
    private TtsVoiceEntity ttsVoice;

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

    public CampaignEntity getCampaign() {
        return campaign;
    }

    public void setCampaign(CampaignEntity campaign) {
        this.campaign = campaign;
    }

    public Long getCampaignId() {
        return campaign != null ? campaign.getId() : null;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AiAgentEntity getAiAgent() {
        return aiAgent;
    }

    public void setAiAgent(AiAgentEntity aiAgent) {
        this.aiAgent = aiAgent;
    }

    public Long getAiAgentId() {
        return aiAgent != null ? aiAgent.getId() : null;
    }

    public String getPromptOverride() {
        return promptOverride;
    }

    public void setPromptOverride(String promptOverride) {
        this.promptOverride = promptOverride;
    }

    public TtsVoiceEntity getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(TtsVoiceEntity ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public String getTtsVoiceId() {
        return ttsVoice != null ? ttsVoice.getId() : null;
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
