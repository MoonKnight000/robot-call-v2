package uz.murodjon.robotcallv2.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

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
            resolvePublic(host);
        } catch (Exception e) {
            log.warn("Refusing host {}: {}", host, e.getMessage());
            return null;
        }
        return uri;
    }

    /**
     * Resolves {@code host} and hands back its addresses only when every one of them is
     * public.
     *
     * <p>This is what pins a request to vetted addresses. Screening a URL and then letting
     * the HTTP client look the name up again leaves a window a short-TTL DNS record walks
     * through (DNS rebinding): public at the check, {@code 127.0.0.1} at the connect. The
     * shared client ({@code shared/http/PublicHttpClientFactory}) resolves through this
     * method at connect time, so the socket opens to an address judged here.
     *
     * @throws UnknownHostException when the host does not resolve, or any of its addresses
     *                              is internal
     */
    public static InetAddress[] resolvePublic(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            // getAllByName("") answers with the loopback address.
            throw new UnknownHostException("empty host");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new UnknownHostException("host " + host + " resolves to internal address "
                        + address.getHostAddress());
            }
        }
        return addresses;
    }

    private static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // What the JDK helpers miss: 0.0.0.0/8 (which Linux routes to the local host),
            // 100.64/10 (carrier-grade NAT — some clouds' internal fabric) and 198.18/15
            // (benchmarking, used inside some provider networks).
            return first == 0
                    || (first == 100 && (second & 0xC0) == 64)
                    || (first == 198 && (second & 0xFE) == 18);
        }
        // ::ffff:a.b.c.d reaches the IPv4 host in its last four bytes, so judge that one.
        if (isIpv4Mapped(bytes)) {
            try {
                return isInternal(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
            } catch (UnknownHostException e) {
                return true;
            }
        }
        // NAT64 (64:ff9b::/96) and 6to4 (2002::/16) also carry an IPv4 address and are
        // translated or relayed to it; no webhook target of ours lives there.
        if (isNat64(bytes) || ((bytes[0] & 0xFF) == 0x20 && (bytes[1] & 0xFF) == 0x02)) {
            return true;
        }
        // isSiteLocalAddress covers 10/8, 172.16/12 and 192.168/16, but for IPv6 only the
        // deprecated fec0::/10. Docker hands out fd00::/8 addresses from fc00::/7, so that
        // range has to be named here or a container address passes as public.
        return (bytes[0] & 0xFE) == 0xFC;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    private static boolean isNat64(byte[] bytes) {
        if (bytes[0] != 0 || (bytes[1] & 0xFF) != 0x64 || (bytes[2] & 0xFF) != 0xFF || (bytes[3] & 0xFF) != 0x9B) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
