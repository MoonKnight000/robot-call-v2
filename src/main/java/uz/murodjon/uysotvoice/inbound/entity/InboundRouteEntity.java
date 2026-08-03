package uz.murodjon.uysotvoice.inbound.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

/** JPA entity for {@code inbound_route} (ROADMAP C.1). */
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

    @Column(name = "scenario_id", nullable = false)
    private long scenarioId;

    @Column(nullable = false)
    private String language;

    @Column(name = "business_hours_start")
    private LocalTime businessHoursStart;

    @Column(name = "business_hours_end")
    private LocalTime businessHoursEnd;

    @Column(name = "fallback_message")
    private String fallbackMessage;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
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

    public long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(long scenarioId) {
        this.scenarioId = scenarioId;
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
