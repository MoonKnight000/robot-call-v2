package uz.murodjon.uysotvoice.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot CRM write-back settings. Bound from {@code voice-agent.crm.*} (Stage 9).
 *
 * @param enabled    off by default; when off, no note is posted and no client is fetched
 * @param baseUrl    CRM base URL
 * @param apiToken   bearer token for the CRM API
 * @param notePath   path appended to baseUrl for creating a note
 * @param clientPath path appended to baseUrl to read one client, with {@code {id}} where
 *                   the client id goes. Used to fill the debtor facts and the preferred
 *                   language before dialling (§3.1, §9 step 4) — the CRM is the
 *                   authoritative source, and imported {@code context_data} is a snapshot
 *                   that may be stale by the time the call goes out. Blank disables the
 *                   lookup and keeps the imported values
 */
@ConfigurationProperties(prefix = "voice-agent.crm")
public record CrmProperties(
        boolean enabled,
        String baseUrl,
        String apiToken,
        String notePath,
        String clientPath
) {
}
