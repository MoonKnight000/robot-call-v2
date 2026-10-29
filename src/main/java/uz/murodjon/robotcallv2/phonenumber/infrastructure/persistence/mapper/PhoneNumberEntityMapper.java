package uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.entity.PhoneNumberEntity;

@Component
public class PhoneNumberEntityMapper {

    public PhoneNumber toPhoneNumber(PhoneNumberEntity entity) {
        return PhoneNumber.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .phoneNumber(entity.getPhoneNumber())
                .nickname(entity.getNickname())
                .type(entity.getType())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .monthlyFee(entity.getMonthlyFee())
                .pricePerChannel(entity.getPricePerChannel())
                .maxSupportedChannels(entity.getMaxSupportedChannels())
                .allocatedChannels(entity.getAllocatedChannels())
                .providerTrunkId(entity.getProviderTrunkId())
                .inboundAgentId(entity.getInboundAgentId())
                .inboundCallFlowId(entity.getInboundCallFlowId())
                .autoRenew(entity.isAutoRenew())
                .purchasedAt(entity.getPurchasedAt())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }

    /** Copies every writable field onto a new or loaded entity; id and created_at stay the adapter's. */
    public void copyToEntity(PhoneNumber domain, CompanyEntity company, PhoneNumberEntity entity) {
        entity.setCompany(company);
        entity.setCompanyId(domain.companyId());
        entity.setPhoneNumber(domain.phoneNumber());
        entity.setNickname(domain.nickname());
        entity.setType(domain.type());
        entity.setCategory(domain.category());
        entity.setStatus(domain.status());
        entity.setMonthlyFee(domain.monthlyFee());
        entity.setPricePerChannel(domain.pricePerChannel());
        entity.setMaxSupportedChannels(domain.maxSupportedChannels());
        entity.setAllocatedChannels(domain.allocatedChannels());
        entity.setProviderTrunkId(domain.providerTrunkId());
        entity.setInboundAgentId(domain.inboundAgentId());
        entity.setInboundCallFlowId(domain.inboundCallFlowId());
        entity.setAutoRenew(domain.autoRenew());
        entity.setPurchasedAt(domain.purchasedAt());
        entity.setExpiresAt(domain.expiresAt());
        entity.setCreatedBy(domain.createdBy());
    }
}
