package uz.murodjon.robotcallv2.phonenumber.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberCategory;

import java.math.BigDecimal;

public record AddPoolNumberRequest(
        @NotBlank String phoneNumber,
        PhoneNumberCategory category,
        @NotNull BigDecimal monthlyFee,
        BigDecimal pricePerChannel,
        Integer maxSupportedChannels,
        Long providerTrunkId
) {
}
