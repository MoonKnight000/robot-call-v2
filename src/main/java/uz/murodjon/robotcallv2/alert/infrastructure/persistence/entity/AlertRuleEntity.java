package uz.murodjon.robotcallv2.alert.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;

import java.time.Instant;

/**
 * The {@code last_*} columns are the scheduler's: not updatable here, so saving a settings edit never
 * writes back a value read before the scheduler's latest evaluation. The scheduler writes them with a
 * native update ({@code AlertRuleJpaRepository.recordEvaluation}), which this mapping does not reach.
 */
@Entity
@Table(name = "alert_rule")
public class AlertRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 32)
    private AlertMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparator", nullable = false, length = 8)
    private AlertComparator comparator;

    @Column(name = "threshold", nullable = false)
    private double threshold;

    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes;

    @Column(name = "min_calls", nullable = false)
    private int minCalls;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Column(name = "ai_agent_id")
    private Long aiAgentId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_value", updatable = false)
    private Double lastValue;

    @Column(name = "last_evaluated_at", updatable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "last_triggered_at", updatable = false)
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AlertMetric getMetric() { return metric; }
    public void setMetric(AlertMetric metric) { this.metric = metric; }
    public AlertComparator getComparator() { return comparator; }
    public void setComparator(AlertComparator comparator) { this.comparator = comparator; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public int getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(int windowMinutes) { this.windowMinutes = windowMinutes; }
    public int getMinCalls() { return minCalls; }
    public void setMinCalls(int minCalls) { this.minCalls = minCalls; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }
    public Long getAiAgentId() { return aiAgentId; }
    public void setAiAgentId(Long aiAgentId) { this.aiAgentId = aiAgentId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Double getLastValue() { return lastValue; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
