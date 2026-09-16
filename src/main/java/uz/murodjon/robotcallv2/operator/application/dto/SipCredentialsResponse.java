package uz.murodjon.robotcallv2.operator.application.dto;

/**
 * What a softphone signs in with. Only ever returned to the operator it belongs to (or to
 * an admin resetting it, to provision a desk phone).
 *
 * @param username  the SIP username and PJSIP endpoint, {@code op-<userId>}
 * @param wsUrl     the WebSocket a browser softphone connects to ({@code voice-agent.asterisk.web-test.ws-url})
 * @param extension the number colleagues dial to reach this operator
 */
public record SipCredentialsResponse(
        String username,
        String password,
        String wsUrl,
        String extension,
        boolean webrtc
) {
}
