package uz.murodjon.robotcallv2.phonenumber.domain.entity;

import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberCategory;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Collects a {@link PhoneNumber}'s fields; obtained from {@link PhoneNumber#builder()} or, to change a
 * few fields of an existing number, {@link PhoneNumber#toBuilder()}. Anything not set falls back to
 * the record's defaults.
 */
public class PhoneNumberBuilder {

    private Long id;
    private Long companyId;
    private String phoneNumber;
    private String nickname;
    private PhoneNumberType type;
    private PhoneNumberCategory category;
    private PhoneNumberStatus status;
    private BigDecimal monthlyFee;
    private BigDecimal pricePerChannel;
    private Integer maxSupportedChannels;
    private Integer allocatedChannels;
    private Long providerTrunkId;
    private Long inboundAgentId;
    private Long inboundCallFlowId;
    private boolean autoRenew;
    private Instant purchasedAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Long createdBy;

    PhoneNumberBuilder() {
    }

    PhoneNumberBuilder(PhoneNumber source) {
        this.id = source.id();
        this.companyId = source.companyId();
        this.phoneNumber = source.phoneNumber();
        this.nickname = source.nickname();
        this.type = source.type();
        this.category = source.category();
        this.status = source.status();
        this.monthlyFee = source.monthlyFee();
        this.pricePerChannel = source.pricePerChannel();
        this.maxSupportedChannels = source.maxSupportedChannels();
        this.allocatedChannels = source.allocatedChannels();
        this.providerTrunkId = source.providerTrunkId();
        this.inboundAgentId = source.inboundAgentId();
        this.inboundCallFlowId = source.inboundCallFlowId();
        this.autoRenew = source.autoRenew();
        this.purchasedAt = source.purchasedAt();
        this.expiresAt = source.expiresAt();
        this.createdAt = source.createdAt();
        this.createdBy = source.createdBy();
    }

    public PhoneNumberBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public PhoneNumberBuilder companyId(Long companyId) {
        this.companyId = companyId;
        return this;
    }

    public PhoneNumberBuilder phoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    public PhoneNumberBuilder nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public PhoneNumberBuilder type(PhoneNumberType type) {
        this.type = type;
        return this;
    }

    public PhoneNumberBuilder category(PhoneNumberCategory category) {
        this.category = category;
        return this;
    }

    public PhoneNumberBuilder status(PhoneNumberStatus status) {
        this.status = status;
        return this;
    }

    public PhoneNumberBuilder monthlyFee(BigDecimal monthlyFee) {
        this.monthlyFee = monthlyFee;
        return this;
    }

    public PhoneNumberBuilder pricePerChannel(BigDecimal pricePerChannel) {
        this.pricePerChannel = pricePerChannel;
        return this;
    }

    public PhoneNumberBuilder maxSupportedChannels(Integer maxSupportedChannels) {
        this.maxSupportedChannels = maxSupportedChannels;
        return this;
    }

    public PhoneNumberBuilder allocatedChannels(Integer allocatedChannels) {
        this.allocatedChannels = allocatedChannels;
        return this;
    }

    public PhoneNumberBuilder providerTrunkId(Long providerTrunkId) {
        this.providerTrunkId = providerTrunkId;
        return this;
    }

    public PhoneNumberBuilder inboundAgentId(Long inboundAgentId) {
        this.inboundAgentId = inboundAgentId;
        return this;
    }

    public PhoneNumberBuilder inboundCallFlowId(Long inboundCallFlowId) {
        this.inboundCallFlowId = inboundCallFlowId;
        return this;
    }

    public PhoneNumberBuilder autoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
        return this;
    }

    public PhoneNumberBuilder purchasedAt(Instant purchasedAt) {
        this.purchasedAt = purchasedAt;
        return this;
    }

    public PhoneNumberBuilder expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public PhoneNumberBuilder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public PhoneNumberBuilder createdBy(Long createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public PhoneNumber build() {
        return new PhoneNumber(id, companyId, phoneNumber, nickname, type, category, status, monthlyFee,
                pricePerChannel, maxSupportedChannels, allocatedChannels, providerTrunkId, inboundAgentId,
                inboundCallFlowId, autoRenew, purchasedAt, expiresAt, createdAt, createdBy);
    }
}
