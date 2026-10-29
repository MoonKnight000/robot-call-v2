package uz.murodjon.robotcallv2.phonenumber.application.dto;

public record UpdateInboundRoutingRequest(
        String nickname,
        Long inboundAgentId,
        Long inboundCallFlowId,
        Boolean autoRenew
) {
}
