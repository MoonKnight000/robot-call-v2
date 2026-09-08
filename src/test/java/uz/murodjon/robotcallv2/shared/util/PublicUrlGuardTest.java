package uz.murodjon.robotcallv2.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard exists so a URL a customer typed cannot turn this process into a request
 * forger against its own network. What is worth pinning down is therefore the refusals:
 * every address that would reach something inside the perimeter, and every shape that is
 * not an address this server should be dialling at all.
 */
class PublicUrlGuardTest {

    @Test
    void refusesTheCloudMetadataAddress() {
        assertThat(PublicUrlGuard.parsePublic("http://169.254.169.254/latest/meta-data/")).isNull();
    }

    @Test
    void refusesLoopback() {
        assertThat(PublicUrlGuard.parsePublic("http://127.0.0.1:8080/actuator")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://localhost:8080/actuator")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://[::1]/actuator")).isNull();
    }

    @Test
    void refusesPrivateRanges() {
        assertThat(PublicUrlGuard.parsePublic("http://10.1.2.3/internal")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://172.16.0.5/internal")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://192.168.1.1/internal")).isNull();
    }

    /**
     * isSiteLocalAddress does not cover fc00::/7, which is the range Docker hands out —
     * without the explicit check a container address passes as public.
     */
    @Test
    void refusesIpv6UniqueLocal() {
        assertThat(PublicUrlGuard.parsePublic("http://[fd00::1]/internal")).isNull();
    }

    @Test
    void refusesSchemesThatAreNotHttp() {
        assertThat(PublicUrlGuard.parsePublic("file:///etc/passwd")).isNull();
        assertThat(PublicUrlGuard.parsePublic("gopher://example.com/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("//example.com/no-scheme")).isNull();
    }

    @Test
    void refusesWhatIsNotAUrlAtAll() {
        assertThat(PublicUrlGuard.parsePublic(null)).isNull();
        assertThat(PublicUrlGuard.parsePublic("   ")).isNull();
        assertThat(PublicUrlGuard.parsePublic("https://")).isNull();
        assertThat(PublicUrlGuard.parsePublic("https://exa mple.com/")).isNull();
    }

    @Test
    void allowsAPublicAddress() {
        assertThat(PublicUrlGuard.parsePublic("https://8.8.8.8/resolve?name=a")).isNotNull();
    }

    /** Whitespace around a pasted URL is the user's, not a reason to refuse it. */
    @Test
    void trimsBeforeParsing() {
        assertThat(PublicUrlGuard.parsePublic("  https://8.8.8.8/x  ")).isNotNull();
    }
}
