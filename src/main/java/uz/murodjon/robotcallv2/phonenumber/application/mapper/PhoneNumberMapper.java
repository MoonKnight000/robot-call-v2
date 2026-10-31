package uz.murodjon.robotcallv2.phonenumber.application.mapper;

import uz.murodjon.robotcallv2.phonenumber.application.dto.PhoneNumberRow;
import uz.murodjon.robotcallv2.phonenumber.application.dto.TestSipConnectionResponse;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.SipProbeResult;

public final class PhoneNumberMapper {

    private PhoneNumberMapper() {
    }

    public static PhoneNumberRow toRow(PhoneNumber number, String agentName, String callFlowName) {
        if (number == null) {
            return null;
        }
        return new PhoneNumberRow(
                number.id() != null ? number.id() : 0L,
                number.companyId(),
                number.phoneNumber(),
                number.nickname(),
                number.type(),
                number.category(),
                number.status(),
                number.monthlyFee(),
                number.pricePerChannel(),
                number.maxSupportedChannels(),
                number.allocatedChannels(),
                number.calculateTotalMonthlyCost(),
                number.providerTrunkId(),
                number.inboundAgentId(),
                agentName,
                number.inboundCallFlowId(),
                callFlowName,
                number.autoRenew(),
                number.purchasedAt(),
                number.expiresAt(),
                number.createdAt()
        );
    }

    public static TestSipConnectionResponse toTestSipConnectionResponse(SipProbeResult result) {
        return new TestSipConnectionResponse(result.connected(), result.roundTripMs(), result.status(), result.details());
    }
}
