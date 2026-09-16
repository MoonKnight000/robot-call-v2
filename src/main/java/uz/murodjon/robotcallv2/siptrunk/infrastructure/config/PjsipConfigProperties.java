package uz.murodjon.robotcallv2.siptrunk.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where {@code PjsipConfigWriter} writes generated PJSIP trunk config (report #7).
 *
 * @param enabled        off by default, because it cannot work until an operator has
 *                       wired the directory up. A managed trunk's row still saves with
 *                       this off — it just is not registered with Asterisk
 * @param configDir      must be the <b>same</b> directory Asterisk reads {@code pjsip.conf}
 *                       from, mounted into this app's container (see the {@code app}
 *                       service in docker-compose.yml). Writing anywhere else produces a
 *                       correct file nothing reads
 * @param configFileName the generated file's name inside that directory
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
