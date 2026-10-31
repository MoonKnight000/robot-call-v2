package uz.murodjon.robotcallv2.phonenumber.application.dto;

import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberCategory;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;

import java.math.BigDecimal;
import java.time.Instant;

public record PhoneNumberRow(
        long id,
        Long companyId,
        String phoneNumber,
        String nickname,
        PhoneNumberType type,
        PhoneNumberCategory category,
        PhoneNumberStatus status,
        BigDecimal monthlyFee,
        BigDecimal pricePerChannel,
        int maxSupportedChannels,
        int allocatedChannels,
        BigDecimal totalMonthlyCost,
        Long providerTrunkId,
        Long inboundAgentId,
        String inboundAgentName,
        Long inboundCallFlowId,
        String inboundCallFlowName,
        boolean autoRenew,
        Instant purchasedAt,
        Instant expiresAt,
        Instant createdAt
) {
}
