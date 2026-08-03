package uz.murodjon.uysotvoice.siptrunk.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param pjsipEndpoint the endpoint name configured in Asterisk's {@code pjsip.conf}
 *                      (§ROADMAP B.3) — this project does not manage {@code pjsip.conf}
 *                      itself, only which already-configured endpoint a call uses
 * @param callerId      null falls back to the trunk's company, then the global
 *                      {@code voice-agent.asterisk.caller-id}
 */
public record CreateSipTrunkRequest(
        @NotBlank String name,
        @NotBlank String pjsipEndpoint,
        String callerId
) {
}
