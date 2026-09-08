package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

import java.time.Instant;

@Entity
@Table(name = "conversion_attribution")
public class ConversionAttributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversion_event_id", nullable = false, unique = true)
    private ConversionEventEntity conversionEvent;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "conversion_event_id", insertable = false, updatable = false)
    private Long conversionEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private CampaignEntity campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private CampaignVariantEntity variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_attempt_id")
    private CallAttemptEntity callAttempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_model", nullable = false, length = 24)
    private AttributionModel attributionModel;

    @Column(name = "window_hours", nullable = false)
    private Integer windowHours;

    @Column(name = "attributed_value_uzs")
    private Long attributedValueUzs;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    @PrePersist
    public void prePersist() {
        if (computedAt == null) {
            computedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public void setConversionEvent(ConversionEventEntity conversionEvent) { this.conversionEvent = conversionEvent; }
    public Long getConversionEventId() { return conversionEvent != null ? conversionEvent.getId() : null; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public CampaignEntity getCampaign() { return campaign; }
    public void setCampaign(CampaignEntity campaign) { this.campaign = campaign; }
    public Long getCampaignId() { return campaign != null ? campaign.getId() : null; }
    public CampaignVariantEntity getVariant() { return variant; }
    public void setVariant(CampaignVariantEntity variant) { this.variant = variant; }
    public Long getVariantId() { return variant != null ? variant.getId() : null; }
    public void setCallAttempt(CallAttemptEntity callAttempt) { this.callAttempt = callAttempt; }
    public Long getCallAttemptId() { return callAttempt != null ? callAttempt.getId() : null; }
    public AttributionModel getAttributionModel() { return attributionModel; }
    public void setAttributionModel(AttributionModel attributionModel) { this.attributionModel = attributionModel; }
    public Integer getWindowHours() { return windowHours; }
    public void setWindowHours(Integer windowHours) { this.windowHours = windowHours; }
    public Long getAttributedValueUzs() { return attributedValueUzs; }
    public void setAttributedValueUzs(Long attributedValueUzs) { this.attributedValueUzs = attributedValueUzs; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
