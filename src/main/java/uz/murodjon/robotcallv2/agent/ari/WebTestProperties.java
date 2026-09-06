package uz.murodjon.robotcallv2.agent.ari;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the web UI needs to place a browser test call ({@code POST /api/calls/web-test}):
 * the Asterisk WebSocket the browser connects to, the {@code [webtest]} PJSIP account it
 * authenticates as, and the extension that lands in the Stasis app
 * (asterisk/etc/asterisk/pjsip.conf, extensions.conf).
 *
 * @param wsUrl       {@code ws://host:8088/ws} (or {@code wss://} when the UI is served
 *                    over https); blank disables the feature
 * @param sipUser     PJSIP endpoint / auth username the browser dials as
 * @param sipPassword its password
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
