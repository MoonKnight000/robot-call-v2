package uz.murodjon.robotcallv2.shared.util;

import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void refusesRangesTheJdkHelpersMiss() {
        assertThat(PublicUrlGuard.parsePublic("http://0.1.2.3/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://100.64.0.1/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://100.127.255.254/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://198.18.0.1/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://198.19.255.1/")).isNull();
    }

    @Test
    void refusesIpv4HiddenInsideIpv6() {
        assertThat(PublicUrlGuard.parsePublic("http://[::ffff:10.0.0.1]/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://[::ffff:169.254.169.254]/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://[64:ff9b::a00:1]/")).isNull();
        assertThat(PublicUrlGuard.parsePublic("http://[2002:a00:1::1]/")).isNull();
    }

    @Test
    void stillAllowsTheNeighboursOfTheNewRanges() {
        assertThat(PublicUrlGuard.parsePublic("http://100.128.0.1/")).isNotNull();
        assertThat(PublicUrlGuard.parsePublic("http://198.20.0.1/")).isNotNull();
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

    /** What the shared HTTP client connects to: only addresses judged here, never a second lookup. */
    @Test
    void resolvesOnlyToPublicAddresses() throws Exception {
        assertThat(PublicUrlGuard.resolvePublic("8.8.8.8")[0].getHostAddress()).isEqualTo("8.8.8.8");
        assertThatThrownBy(() -> PublicUrlGuard.resolvePublic("127.0.0.1")).isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> PublicUrlGuard.resolvePublic("localhost")).isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> PublicUrlGuard.resolvePublic("::ffff:10.0.0.1")).isInstanceOf(UnknownHostException.class);
        // getAllByName("") would answer with the loopback address.
        assertThatThrownBy(() -> PublicUrlGuard.resolvePublic("")).isInstanceOf(UnknownHostException.class);
    }
}
