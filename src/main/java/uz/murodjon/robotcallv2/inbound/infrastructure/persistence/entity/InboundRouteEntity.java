package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;

import java.time.Instant;
import java.time.LocalTime;

/**
 * JPA entity for inbound_route (ROADMAP C.1).
 */
@Entity
@Table(name = "inbound_route")
public class InboundRouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "did_number", nullable = false)
    private String didNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_agent_id")
    private AiAgentEntity aiAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_hours_action", nullable = false)
    private InboundAfterHoursAction afterHoursAction = InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP;

    @Column(name = "after_hours_destination")
    private String afterHoursDestination;

    @Column(name = "business_hours_start")
    private LocalTime businessHoursStart;

    @Column(name = "business_hours_end")
    private LocalTime businessHoursEnd;

    @Column(name = "fallback_message")
    private String fallbackMessage;

    @Column(name = "callback_campaign_id")
    private Long callbackCampaignId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "call_flow_id")
    private Long callFlowId;

    @Column(nullable = false)
    private boolean enabled = true;

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

    public String getDidNumber() {
        return didNumber;
    }

    public void setDidNumber(String didNumber) {
        this.didNumber = didNumber;
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

    public InboundAfterHoursAction getAfterHoursAction() {
        return afterHoursAction;
    }

    public void setAfterHoursAction(InboundAfterHoursAction afterHoursAction) {
        this.afterHoursAction = afterHoursAction != null ? afterHoursAction : InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP;
    }

    public String getAfterHoursDestination() {
        return afterHoursDestination;
    }

    public void setAfterHoursDestination(String afterHoursDestination) {
        this.afterHoursDestination = afterHoursDestination;
    }


    public LocalTime getBusinessHoursStart() {
        return businessHoursStart;
    }

    public void setBusinessHoursStart(LocalTime businessHoursStart) {
        this.businessHoursStart = businessHoursStart;
    }

    public LocalTime getBusinessHoursEnd() {
        return businessHoursEnd;
    }

    public void setBusinessHoursEnd(LocalTime businessHoursEnd) {
        this.businessHoursEnd = businessHoursEnd;
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public void setFallbackMessage(String fallbackMessage) {
        this.fallbackMessage = fallbackMessage;
    }

    public Long getCallbackCampaignId() {
        return callbackCampaignId;
    }

    public void setCallbackCampaignId(Long callbackCampaignId) {
        this.callbackCampaignId = callbackCampaignId;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public Long getCallFlowId() {
        return callFlowId;
    }

    public void setCallFlowId(Long callFlowId) {
        this.callFlowId = callFlowId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
