package uz.murodjon.uysotvoice.siptrunk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where {@code siptrunk.service.PjsipConfigWriter} writes the generated PJSIP trunk
 * config (report #7). Off by default — writing a file and asking Asterisk to reload
 * only makes sense once an operator has {@code #include}d {@code configFileName} from
 * the real {@code pjsip.conf} and mounted {@code configDir} into this app's container
 * (it is not, by default — see {@code docker-compose.yml}'s {@code app} service).
 *
 * @param enabled        when false, {@code PjsipConfigWriter} is a no-op — a managed
 *                        trunk's row is still saved, just not registered with Asterisk yet
 * @param configDir       must be the exact directory Asterisk itself reads {@code
 *                        pjsip.conf} from (typically {@code /etc/asterisk})
 * @param configFileName  the generated file's name; {@code pjsip.conf} must {@code
 *                        #include} it. Defaults to {@code "pjsip_trunks.conf"}
 */
@ConfigurationProperties(prefix = "voice-agent.siptrunk")
public record PjsipConfigProperties(
        boolean enabled,
        String configDir,
        String configFileName
) {

    public PjsipConfigProperties {
        if (configFileName == null || configFileName.isBlank()) {
            configFileName = "pjsip_trunks.conf";
        }
    }
}
