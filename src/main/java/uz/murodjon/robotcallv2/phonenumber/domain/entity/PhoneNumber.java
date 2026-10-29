package uz.murodjon.robotcallv2.phonenumber.domain.entity;

import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberCategory;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a phone number: either purchased from platform pool or connected via BYO SIP trunk.
 */
public record PhoneNumber(
        Long id,
        Long companyId,
        String phoneNumber,
        String nickname,
        PhoneNumberType type,
        PhoneNumberCategory category,
        PhoneNumberStatus status,
        BigDecimal monthlyFee,
        BigDecimal pricePerChannel,
        Integer maxSupportedChannels,
        Integer allocatedChannels,
        Long providerTrunkId,
        Long inboundAgentId,
        Long inboundCallFlowId,
        boolean autoRenew,
        Instant purchasedAt,
        Instant expiresAt,
        Instant createdAt,
        Long createdBy
) {

    /** The defaults live only here; callers and the entity pass nulls through. */
    public PhoneNumber {
        Objects.requireNonNull(phoneNumber, "phoneNumber cannot be null");
        if (type == null) {
            type = PhoneNumberType.MARKETPLACE_PURCHASED;
        }
        if (category == null) {
            category = PhoneNumberCategory.STANDARD;
        }
        if (status == null) {
            status = PhoneNumberStatus.AVAILABLE;
        }
        if (monthlyFee == null) {
            monthlyFee = BigDecimal.ZERO;
        }
        if (pricePerChannel == null) {
            pricePerChannel = BigDecimal.ZERO;
        }
        if (maxSupportedChannels == null) {
            maxSupportedChannels = 10;
        }
        if (allocatedChannels == null) {
            allocatedChannels = 1;
        }
    }

    public static PhoneNumberBuilder builder() {
        return new PhoneNumberBuilder();
    }

    public PhoneNumberBuilder toBuilder() {
        return new PhoneNumberBuilder(this);
    }

    public boolean isAssignedToCompany(long checkCompanyId) {
        return companyId != null && companyId == checkCompanyId && status == PhoneNumberStatus.ASSIGNED;
    }

    public BigDecimal calculateTotalMonthlyCost() {
        return monthlyFee.add(pricePerChannel.multiply(BigDecimal.valueOf(allocatedChannels)));
    }
}
