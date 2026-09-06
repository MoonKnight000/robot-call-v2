package uz.murodjon.robotcallv2.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * API access settings. Bound from {@code voice-agent.security.*}.
 *
 * @param publicUi      serve the static test panel ({@code /}, {@code /index.html}) and the
 *                      springdoc UI without logging in. The panel itself still sends its
 *                      Bearer token with every call it makes, so this only exposes the page,
 *                      not the API.
 * @param allowedOrigins browser origins (scheme+host+port, e.g. {@code http://localhost:5173})
 *                      that may call the API cross-origin. The static panel is served from
 *                      this app's own origin so it never needs this; a separately hosted
 *                      frontend dev server does. Blank/empty disables CORS entirely.
 */
@ConfigurationProperties(prefix = "voice-agent.security")
public record SecurityProperties(
        boolean publicUi,
        List<String> allowedOrigins
) {
}
