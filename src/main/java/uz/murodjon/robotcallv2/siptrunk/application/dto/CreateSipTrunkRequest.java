package uz.murodjon.robotcallv2.siptrunk.application.dto;

import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.util.List;

public record CreateSipTrunkRequest(
        @NotBlank String name,
        String pjsipEndpoint,
        String host,
        Integer port,
        String sipUsername,
        String sipPassword,
        SipTrunkTransport transport,
        List<String> codecs,
        String callerId
) {
}
