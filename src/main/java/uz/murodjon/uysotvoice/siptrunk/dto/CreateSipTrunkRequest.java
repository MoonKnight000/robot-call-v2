package uz.murodjon.uysotvoice.siptrunk.dto;

import jakarta.validation.constraints.NotBlank;

import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;

/**
 * Two mutually exclusive modes (report #7), validated in {@code SipTrunkService}
 * (not annotations — an either/or across fields needs real logic):
 *
 * @param pjsipEndpoint <strong>Manual mode</strong> — the endpoint name already
 *                      configured by hand in {@code pjsip.conf} (§ROADMAP B.3 original
 *                      shape). Mutually exclusive with {@code host}/{@code
 *                      sipUsername}/{@code sipPassword}.
 * @param host          <strong>Managed mode</strong> — the SIP provider's host/domain.
 *                      When set (with {@code sipUsername}/{@code sipPassword}), the app
 *                      generates the {@code pjsipEndpoint} itself, writes matching PJSIP
 *                      config, and registers it with Asterisk.
 * @param port          managed mode; blank/omitted defaults to 5060
 * @param transport     managed mode; blank/omitted defaults to {@code UDP} — {@code
 *                      TCP}/{@code TLS} are rejected for now, see {@link SipTrunkTransport}
 * @param callerId      null falls back to the trunk's company, then the global
 *                      {@code voice-agent.asterisk.caller-id}
 */
public record CreateSipTrunkRequest(
        @NotBlank String name,
        String pjsipEndpoint,
        String host,
        Integer port,
        String sipUsername,
        String sipPassword,
        SipTrunkTransport transport,
        String callerId
) {
}
