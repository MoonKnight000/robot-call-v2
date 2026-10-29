package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The ai_agent columns behind {@code AiAgentLimits}. Every column is nullable, so Hibernate
 * loads an agent with no limits as a null embeddable — {@link AiAgentEntity#getLimits()}
 * hides that.
 */
@Embeddable
public class AiAgentLimitsEntity {

    @Column(name = "max_conversation_duration_seconds")
    private Integer maxConversationDurationSeconds;

    @Column(name = "silence_end_call_timeout_seconds")
    private Integer silenceEndCallTimeoutSeconds;

    @Column(name = "concurrent_calls_limit")
    private Integer concurrentCallsLimit;

    @Column(name = "daily_calls_limit")
    private Integer dailyCallsLimit;

    protected AiAgentLimitsEntity() {
    }

    public AiAgentLimitsEntity(Integer maxConversationDurationSeconds, Integer silenceEndCallTimeoutSeconds,
                               Integer concurrentCallsLimit, Integer dailyCallsLimit) {
        this.maxConversationDurationSeconds = maxConversationDurationSeconds;
        this.silenceEndCallTimeoutSeconds = silenceEndCallTimeoutSeconds;
        this.concurrentCallsLimit = concurrentCallsLimit;
        this.dailyCallsLimit = dailyCallsLimit;
    }

    public Integer getMaxConversationDurationSeconds() {
        return maxConversationDurationSeconds;
    }

    public Integer getSilenceEndCallTimeoutSeconds() {
        return silenceEndCallTimeoutSeconds;
    }

    public Integer getConcurrentCallsLimit() {
        return concurrentCallsLimit;
    }

    public Integer getDailyCallsLimit() {
        return dailyCallsLimit;
    }
}
