package uz.murodjon.robotcallv2.siptrunk.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where PjsipConfigWriter writes generated PJSIP trunk config (report #7).
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
