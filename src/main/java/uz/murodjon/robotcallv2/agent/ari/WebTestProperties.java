package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the web UI needs to place a browser test call ({@code POST /api/calls/web-test}):
 * the Asterisk WebSocket the browser connects to, the {@code [webtest]} PJSIP account it
 * authenticates as, and the extension that lands in the Stasis app
 * (asterisk/etc/asterisk/pjsip.conf, extensions.conf).
 *
 * <p>The browser dials {@code dialNumber}, which lands in the Stasis app exactly as a
 * softphone dialling 600 does — so this exercises the real inbound path, not a mock.
 *
 * @param wsUrl       {@code ws://host:8088/ws}, or {@code wss://} ({@code http.conf} TLS)
 *                    when the UI itself is served over https. It has to be reachable
 *                    <em>from the browser</em>: the host's LAN IP, never {@code asterisk}.
 *                    Blank disables the endpoint (409 {@code WEB_TEST_NOT_CONFIGURED})
 * @param sipUser     PJSIP endpoint / auth username the browser dials as
 * @param sipPassword its password — must match {@code pjsip.conf [webtest-auth]}
 * @param dialNumber  extension the browser dials to reach the AI
 */
@ConfigurationProperties(prefix = "voice-agent.asterisk.web-test")
public record WebTestProperties(
        String wsUrl,
        String sipUser,
        String sipPassword,
        String dialNumber
) {
}
