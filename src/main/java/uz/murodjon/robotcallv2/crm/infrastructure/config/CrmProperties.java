package uz.murodjon.robotcallv2.crm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot CRM write-back settings. Bound from {@code voice-agent.crm.*} (Stage 9, MASTER_ROADMAP.md §11).
 */
@ConfigurationProperties(prefix = "voice-agent.crm")
public record CrmProperties(
        boolean enabled,
        String baseUrl,
        String apiToken,
        String notePath,
        String clientPath,
        String clientByPhonePath,
        String callHistoryPath
) {
}
