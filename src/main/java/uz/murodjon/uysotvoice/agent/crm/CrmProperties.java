package uz.murodjon.uysotvoice.agent.crm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot CRM write-back settings. Bound from {@code voice-agent.crm.*} (Stage 9).
 *
 * @param enabled  off by default; when off, no note is posted
 * @param baseUrl  CRM base URL
 * @param apiToken bearer token for the CRM API
 * @param notePath path appended to baseUrl for creating a note
 */
@ConfigurationProperties(prefix = "voice-agent.crm")
public record CrmProperties(
        boolean enabled,
        String baseUrl,
        String apiToken,
        String notePath
) {
}
