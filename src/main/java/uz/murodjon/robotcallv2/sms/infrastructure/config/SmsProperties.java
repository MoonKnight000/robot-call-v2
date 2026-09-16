package uz.murodjon.robotcallv2.sms.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS gateway settings. Bound from {@code voice-agent.sms.*}.
 *
 * @param enabled       false logs what would be sent and sends nothing
 * @param eskiz         Eskiz gateway credentials
 * @param defaultSender sender name/number shown to the recipient
 */
@ConfigurationProperties(prefix = "voice-agent.sms")
public record SmsProperties(boolean enabled, SmsEskizProperties eskiz, String defaultSender) {

    public SmsProperties {
        if (eskiz == null) {
            eskiz = new SmsEskizProperties(null, null, null, null);
        }
        if (defaultSender == null || defaultSender.isBlank()) {
            defaultSender = "4546";
        }
    }
}
