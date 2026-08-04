package uz.murodjon.uysotvoice.agent.ami;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Asterisk Manager Interface (AMI) connection settings — bound from {@code
 * voice-agent.asterisk.ami.*}. The only thing this app uses AMI for today is telling
 * Asterisk to reload {@code res_pjsip.so} after {@code siptrunk.service.PjsipConfigWriter}
 * regenerates the generated-trunks file (report #7); everything else (call control) goes
 * through ARI ({@code agent.ari.AsteriskProperties}), which has no reload action.
 *
 * <p>Requires {@code manager.conf} configured on the Asterisk side — this app cannot
 * provision that itself.
 *
 * @param enabled  when false, {@link AmiClient} never opens a connection — a managed
 *                 trunk's config is still written, just not live-reloaded (Asterisk
 *                 picks it up on its own next restart/manual reload)
 * @param host     AMI TCP host (usually the same host as ARI)
 * @param port     AMI TCP port — {@code manager.conf} default is 5038
 * @param username AMI user ({@code manager.conf})
 * @param password AMI secret ({@code manager.conf})
 */
@ConfigurationProperties(prefix = "voice-agent.asterisk.ami")
public record AmiProperties(
        boolean enabled,
        String host,
        int port,
        String username,
        String password
) {

    public AmiProperties {
        if (port <= 0) {
            port = 5038;
        }
    }
}
