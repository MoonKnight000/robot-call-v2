package uz.murodjon.uysotvoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API access settings. Bound from {@code voice-agent.security.*}.
 *
 * @param apiKey     admin secret expected in the {@code X-Api-Key} header. Grants
 *                   everything, including originating calls and starting campaigns. Blank
 *                   means no request can authenticate — the API is closed, not open
 *                   (fail-closed).
 * @param readApiKey optional second secret that may only read. Reporting, transcripts and
 *                   recordings are what most people actually need, and handing out the key
 *                   that can dial 50 000 subscribers to get them is the wrong trade. Blank
 *                   disables the read-only role entirely.
 * @param publicUi   serve the static test panel ({@code /}, {@code /index.html}) and the
 *                   springdoc UI without a key. The panel itself still sends the key with
 *                   every call it makes, so this only exposes the page, not the API.
 */
@ConfigurationProperties(prefix = "voice-agent.security")
public record SecurityProperties(
        String apiKey,
        String readApiKey,
        boolean publicUi
) {

    /** Whether a usable admin key is configured at all. */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Whether a separate read-only key is in play. */
    public boolean readOnlyConfigured() {
        return readApiKey != null && !readApiKey.isBlank();
    }
}
