package uz.murodjon.robotcallv2.widget.application.dto;

/**
 * Everything the embed script needs to put a visitor through to the agent: connect a
 * SIP-over-WebSocket client to {@code wsUrl} as {@code sipUser}/{@code sipPassword} and
 * INVITE {@code dialNumber} carrying the header {@code sessionHeader: sessionId}.
 *
 * <p>The same shape the operator console uses for a browser test call — from Asterisk's
 * side a visitor and a tester arrive identically — and the credentials are the shared
 * WebRTC account, not anything belonging to the visitor. The session id is what actually
 * authorises the call, it is single-use, and it expires in five minutes.
 *
 * <p>Handing those credentials to an anonymous visitor is safe only because they are not
 * a capability on their own: the {@code webtest} endpoint sits in Asterisk's
 * {@code [from-webrtc]} context, whose single extension hangs up any call that does not
 * carry a session id this application issued. Getting one costs an allowed origin and a
 * slot from the agent's call limits. If that endpoint is ever pointed at a context that
 * can dial a trunk, this response becomes toll fraud — see {@code pjsip.conf}.
 */
public record PublicWidgetSessionResponse(
        String sessionId,
        String wsUrl,
        String sipUser,
        String sipPassword,
        String dialNumber,
        String sessionHeader
) {
}
