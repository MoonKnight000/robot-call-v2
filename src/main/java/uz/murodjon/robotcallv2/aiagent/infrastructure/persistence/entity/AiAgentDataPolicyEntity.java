package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import uz.murodjon.robotcallv2.aiagent.domain.enums.RealtimeFactsDelivery;

/** The ai_agent columns behind {@code AiAgentDataPolicy}: what a call keeps and for how long. */
@Embeddable
public class AiAgentDataPolicyEntity {

    @Column(name = "zero_pii_retention", nullable = false)
    private boolean zeroPiiRetention;

    @Column(name = "store_call_audio", nullable = false)
    private boolean storeCallAudio = true;

    @Column(name = "conversation_retention_days")
    private Integer conversationRetentionDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "realtime_facts_delivery", length = 10)
    private RealtimeFactsDelivery realtimeFactsDelivery;

    protected AiAgentDataPolicyEntity() {
    }

    public AiAgentDataPolicyEntity(boolean zeroPiiRetention, boolean storeCallAudio,
                                   Integer conversationRetentionDays, RealtimeFactsDelivery realtimeFactsDelivery) {
        this.zeroPiiRetention = zeroPiiRetention;
        this.storeCallAudio = storeCallAudio;
        this.conversationRetentionDays = conversationRetentionDays;
        this.realtimeFactsDelivery = realtimeFactsDelivery;
    }

    public boolean isZeroPiiRetention() {
        return zeroPiiRetention;
    }

    public boolean isStoreCallAudio() {
        return storeCallAudio;
    }

    public Integer getConversationRetentionDays() {
        return conversationRetentionDays;
    }

    public RealtimeFactsDelivery getRealtimeFactsDelivery() {
        return realtimeFactsDelivery;
    }
}
