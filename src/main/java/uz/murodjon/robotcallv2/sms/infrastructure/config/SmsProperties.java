package uz.murodjon.robotcallv2.sms.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS gateway settings. Bound from {@code voice-agent.sms.*}. The account itself is each
 * company's own Eskiz login (Integrations), never a platform-wide one.
 *
 * @param eskiz         Eskiz endpoints shared by every company
 * @param defaultSender sender name/number shown to the recipient when the company set none
 */
@ConfigurationProperties(prefix = "voice-agent.sms")
public record SmsProperties(SmsEskizProperties eskiz, String defaultSender) {

    public SmsProperties {
        if (eskiz == null) {
            eskiz = new SmsEskizProperties(null, null);
        }
        if (defaultSender == null || defaultSender.isBlank()) {
            defaultSender = "4546";
        }
    }
}
