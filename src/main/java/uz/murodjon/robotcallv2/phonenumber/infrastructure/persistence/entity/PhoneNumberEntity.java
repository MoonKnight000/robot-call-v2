package uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberCategory;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "phone_number")
public class PhoneNumberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private CompanyEntity company;

    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "phone_number", nullable = false, unique = true, length = 32)
    private String phoneNumber;

    @Column(name = "nickname", length = 120)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private PhoneNumberType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private PhoneNumberCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PhoneNumberStatus status;

    @Column(name = "monthly_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "price_per_channel", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerChannel;

    @Column(name = "max_supported_channels", nullable = false)
    private Integer maxSupportedChannels;

    @Column(name = "allocated_channels", nullable = false)
    private Integer allocatedChannels;

    @Column(name = "provider_trunk_id")
    private Long providerTrunkId;

    @Column(name = "inbound_agent_id")
    private Long inboundAgentId;

    @Column(name = "inbound_call_flow_id")
    private Long inboundCallFlowId;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "purchased_at")
    private Instant purchasedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

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
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public PhoneNumberType getType() {
        return type;
    }

    public void setType(PhoneNumberType type) {
        this.type = type;
    }

    public PhoneNumberCategory getCategory() {
        return category;
    }

    public void setCategory(PhoneNumberCategory category) {
        this.category = category;
    }

    public PhoneNumberStatus getStatus() {
        return status;
    }

    public void setStatus(PhoneNumberStatus status) {
        this.status = status;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }

    public void setMonthlyFee(BigDecimal monthlyFee) {
        this.monthlyFee = monthlyFee;
    }

    public BigDecimal getPricePerChannel() {
        return pricePerChannel;
    }

    public void setPricePerChannel(BigDecimal pricePerChannel) {
        this.pricePerChannel = pricePerChannel;
    }

    public Integer getMaxSupportedChannels() {
        return maxSupportedChannels;
    }

    public void setMaxSupportedChannels(Integer maxSupportedChannels) {
        this.maxSupportedChannels = maxSupportedChannels;
    }

    public Integer getAllocatedChannels() {
        return allocatedChannels;
    }

    public void setAllocatedChannels(Integer allocatedChannels) {
        this.allocatedChannels = allocatedChannels;
    }

    public Long getProviderTrunkId() {
        return providerTrunkId;
    }

    public void setProviderTrunkId(Long providerTrunkId) {
        this.providerTrunkId = providerTrunkId;
    }

    public Long getInboundAgentId() {
        return inboundAgentId;
    }

    public void setInboundAgentId(Long inboundAgentId) {
        this.inboundAgentId = inboundAgentId;
    }

    public Long getInboundCallFlowId() {
        return inboundCallFlowId;
    }

    public void setInboundCallFlowId(Long inboundCallFlowId) {
        this.inboundCallFlowId = inboundCallFlowId;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(Instant purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
