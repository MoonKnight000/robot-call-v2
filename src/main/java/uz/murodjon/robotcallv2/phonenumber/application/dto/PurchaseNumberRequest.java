package uz.murodjon.robotcallv2.phonenumber.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PurchaseNumberRequest(
        @NotNull Long poolNumberId,
        @NotNull @Min(1) Integer allocatedChannels,
        String nickname,
        Long inboundAgentId,
        Long inboundCallFlowId,
        Boolean autoRenew
) {
}
