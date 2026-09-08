package uz.murodjon.robotcallv2.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;

/**
 * Screens a URL a tenant wrote before this process fetches it.
 *
 * <p>Several features let a company point us at an address of their choosing — a fact
 * webhook, a target source, an HTTP tool the model calls, a post-call webhook. Without a
 * check, each of those is a request forger: {@code http://127.0.0.1:8080/actuator},
 * {@code http://169.254.169.254/} or any host on the Docker network would be fetched by
 * this process from inside the perimeter, and the answer handed back to the tenant — or
 * pasted into a prompt.
 *
 * <p>The rule lives here rather than beside each caller because a security check kept in
 * four copies drifts into three, and the one that stops being updated is the hole.
 *
 * <p>Screening the host is only half of it. A caller must also refuse to follow
 * redirects: a 302 is the endpoint choosing a second host <em>after</em> this check has
 * passed, which is exactly how such a filter is walked around.
 */
public final class PublicUrlGuard {

    private static final Logger log = LoggerFactory.getLogger(PublicUrlGuard.class);

    private PublicUrlGuard() {
    }

    /**
     * @return the parsed URL when this server is willing to call it on a tenant's behalf,
     *         or {@code null} when it is not — the reason is logged here, and what a
     *         refusal means (a 400, or carrying on without the data) is the caller's.
     */
    public static URI parsePublic(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (Exception e) {
            // Never the URL itself: a tool's address carries its credential in the query
            // string by the time it reaches here, and a log line is not the place for it.
            log.warn("Refusing a URL that does not parse");
            return null;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            log.warn("Refusing scheme '{}' — only http(s) is callable", scheme);
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            log.warn("Refusing a URL with no host");
            return null;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternal(address)) {
                    log.warn("Host {} resolves to {} — refusing to call an internal address",
                            host, address.getHostAddress());
                    return null;
                }
            }
        } catch (Exception e) {
            log.warn("Host {} did not resolve: {}", host, e.getMessage());
            return null;
        }
        return uri;
    }

    private static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // isSiteLocalAddress covers 10/8, 172.16/12 and 192.168/16, but for IPv6 only the
        // deprecated fec0::/10. Docker hands out fd00::/8 addresses from fc00::/7, so that
        // range has to be named here or a container address passes as public.
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
