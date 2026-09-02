package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundFailoverAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteType;
import uz.murodjon.robotcallv2.inbound.domain.enums.QueueStrategy;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;

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

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column(name = "did_number", nullable = false)
    private String didNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private ScenarioEntity scenario;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", nullable = false)
    private InboundRouteType routeType = InboundRouteType.SCENARIO;

    @Column(name = "target_destination")
    private String targetDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_strategy", nullable = false)
    private QueueStrategy queueStrategy = QueueStrategy.RING_ALL;

    @Column(name = "ring_timeout_sec", nullable = false)
    private int ringTimeoutSec = 20;

    @Enumerated(EnumType.STRING)
    @Column(name = "failover_action", nullable = false)
    private InboundFailoverAction failoverAction = InboundFailoverAction.SCENARIO;

    @Column(name = "failover_destination")
    private String failoverDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_hours_action", nullable = false)
    private InboundAfterHoursAction afterHoursAction = InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP;

    @Column(name = "after_hours_destination")
    private String afterHoursDestination;

    @Column(name = "ivr_menu_config", columnDefinition = "TEXT")
    private String ivrMenuConfig;

    @Column(nullable = false)
    private String language = "uz-UZ";

    @Column(name = "business_hours_start")
    private LocalTime businessHoursStart;

    @Column(name = "business_hours_end")
    private LocalTime businessHoursEnd;

    @Column(name = "fallback_message")
    private String fallbackMessage;

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

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public String getDidNumber() {
        return didNumber;
    }

    public void setDidNumber(String didNumber) {
        this.didNumber = didNumber;
    }

    public ScenarioEntity getScenario() {
        return scenario;
    }

    public void setScenario(ScenarioEntity scenario) {
        this.scenario = scenario;
    }

    public InboundRouteType getRouteType() {
        return routeType;
    }

    public void setRouteType(InboundRouteType routeType) {
        this.routeType = routeType != null ? routeType : InboundRouteType.SCENARIO;
    }

    public String getTargetDestination() {
        return targetDestination;
    }

    public void setTargetDestination(String targetDestination) {
        this.targetDestination = targetDestination;
    }

    public QueueStrategy getQueueStrategy() {
        return queueStrategy;
    }

    public void setQueueStrategy(QueueStrategy queueStrategy) {
        this.queueStrategy = queueStrategy != null ? queueStrategy : QueueStrategy.RING_ALL;
    }

    public int getRingTimeoutSec() {
        return ringTimeoutSec;
    }

    public void setRingTimeoutSec(int ringTimeoutSec) {
        this.ringTimeoutSec = ringTimeoutSec;
    }

    public InboundFailoverAction getFailoverAction() {
        return failoverAction;
    }

    public void setFailoverAction(InboundFailoverAction failoverAction) {
        this.failoverAction = failoverAction != null ? failoverAction : InboundFailoverAction.SCENARIO;
    }

    public String getFailoverDestination() {
        return failoverDestination;
    }

    public void setFailoverDestination(String failoverDestination) {
        this.failoverDestination = failoverDestination;
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

    public String getIvrMenuConfig() {
        return ivrMenuConfig;
    }

    public void setIvrMenuConfig(String ivrMenuConfig) {
        this.ivrMenuConfig = ivrMenuConfig;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
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
