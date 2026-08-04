package uz.murodjon.uysotvoice.agent.ami;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Asterisk Manager Protocol client (raw TCP, hand-written rather than a full
 * library — the only thing this app needs AMI for is one action). See {@link
 * AmiProperties} for why this exists instead of ARI.
 */
@Component
public class AmiClient {

    private static final Logger log = LoggerFactory.getLogger(AmiClient.class);
    private static final int TIMEOUT_MS = 5000;

    private final AmiProperties props;

    public AmiClient(AmiProperties props) {
        this.props = props;
    }

    /**
     * Reloads {@code res_pjsip.so} so a freshly written PJSIP config file takes effect
     * immediately, instead of waiting for Asterisk's own next restart. Best-effort:
     * never throws, logs and returns {@code false} on any failure — the caller ({@code
     * PjsipConfigWriter}) treats a failed reload as "will pick it up later," not a
     * reason to fail the trunk CRUD request that triggered this.
     */
    public boolean reloadPjsip() {
        if (!props.enabled()) {
            log.debug("AMI disabled (voice-agent.asterisk.ami.enabled=false) — pjsip reload skipped");
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(props.host(), props.port()), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String banner = in.readLine();
            if (banner == null || !banner.startsWith("Asterisk Call Manager")) {
                log.warn("AMI {}:{} — unexpected banner '{}'", props.host(), props.port(), banner);
                return false;
            }

            send(out, "Action: Login\r\nUsername: " + props.username() + "\r\nSecret: " + props.password()
                    + "\r\nEvents: off\r\n\r\n");
            if (!readResponse(in).contains("Response: Success")) {
                log.warn("AMI {}:{} — login failed", props.host(), props.port());
                return false;
            }

            send(out, "Action: Reload\r\nModule: res_pjsip.so\r\n\r\n");
            String reloadResponse = readResponse(in);
            boolean ok = reloadResponse.contains("Response: Success");
            if (!ok) {
                log.warn("AMI {}:{} — pjsip reload failed: {}", props.host(), props.port(),
                        reloadResponse.replace('\n', ' '));
            }

            send(out, "Action: Logoff\r\n\r\n");
            return ok;
        } catch (IOException e) {
            log.warn("AMI {}:{} connection failed: {}", props.host(), props.port(), e.getMessage());
            return false;
        }
    }

    private static void send(OutputStream out, String message) throws IOException {
        out.write(message.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    /** One AMI response block ends at the first blank line. */
    private static String readResponse(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
