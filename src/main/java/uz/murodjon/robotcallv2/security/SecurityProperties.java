package uz.murodjon.robotcallv2.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * API access settings. Bound from {@code voice-agent.security.*}.
 *
 * @param allowedOrigins browser origins (scheme+host+port, e.g. {@code http://localhost:5173})
 *                      that may call the API cross-origin. The static panel is served from
 *                      this app's own origin so it never needs this; a separately hosted
 *                      frontend dev server does. Blank/empty allows no cross-origin caller
 *                      at all — it never falls open to every origin.
 */
@ConfigurationProperties(prefix = "voice-agent.security")
public record SecurityProperties(
        List<String> allowedOrigins
) {
}
