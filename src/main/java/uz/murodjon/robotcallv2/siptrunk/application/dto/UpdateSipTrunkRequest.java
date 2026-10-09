package uz.murodjon.robotcallv2.siptrunk.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.util.List;

public record UpdateSipTrunkRequest(
        @NotBlank String name,
        String pjsipEndpoint,
        String host,
        Integer port,
        String sipUsername,
        String sipPassword,
        SipTrunkTransport transport,
        List<String> codecs,
        String callerId,
        @NotNull Boolean enabled,
        /** Calls per second the provider accepts on this trunk; null = voice-agent.dialer.default-trunk-cps. */
        @Min(1) @Max(100) Integer maxCps
) {
}
