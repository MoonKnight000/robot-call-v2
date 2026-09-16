package uz.murodjon.robotcallv2.shared.http;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/**
 * The one place an HTTP client for a tenant-supplied URL is built.
 *
 * <p>A company can point this process at an address of its choosing — a webhook, a tool,
 * an MCP server, a CRM account, a knowledge-base page. {@link PublicUrlGuard#parsePublic}
 * screens such a URL, but a client that then resolves the host again connects wherever
 * the second DNS answer says, and a record with a one-second TTL can answer "public" to
 * the check and "127.0.0.1" to the connect. Every client built here resolves through
 * {@link PublicUrlGuard#resolvePublic} instead, so the socket only ever opens to an
 * address that was judged public in the same lookup.
 *
 * <p>The rest of the configuration closes the other ways around the check or across
 * tenants: redirects are not followed (a 302 is the far end choosing a second host after
 * the check), nothing is retried behind the caller's back, and there is no cookie or auth
 * cache shared between one company's endpoint and the next.
 */
public final class PublicHttpClientFactory {

    private static final int MAX_CONNECTIONS_TOTAL = 200;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;
    private static final TimeValue IDLE_EVICTION = TimeValue.ofMinutes(1);

    private PublicHttpClientFactory() {
    }

    /**
     * @param connectTimeout how long a TCP/TLS connect, and a wait for a pooled connection, may take
     * @param readTimeout    the default wait for response bytes; a request may set its own
     */
    public static CloseableHttpClient create(Duration connectTimeout, Duration readTimeout) {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new PublicAddressDnsResolver())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .setSocketTimeout(Timeout.of(readTimeout))
                                .build())
                        .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                        .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                        .setResponseTimeout(Timeout.of(readTimeout))
                        .setRedirectsEnabled(false)
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                // As the JDK client did: no Accept-Encoding, so a size cap counts what was sent.
                .disableContentCompression()
                .evictIdleConnections(IDLE_EVICTION)
                .build();
    }

    /** Resolves only to public addresses; an internal one fails the connect as an unknown host. */
    private static final class PublicAddressDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            return PublicUrlGuard.resolvePublic(host);
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            PublicUrlGuard.resolvePublic(host);
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }
}
