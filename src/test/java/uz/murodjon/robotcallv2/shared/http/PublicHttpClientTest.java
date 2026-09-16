package uz.murodjon.robotcallv2.shared.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client resolves hosts through PublicUrlGuard at connect time, so a URL that reaches
 * it without being screened — or whose name now points inside the network — still never
 * opens a socket to an internal address. Nothing here leaves the machine: the refusal
 * happens in the resolver, before any connect.
 */
class PublicHttpClientTest {

    private final PublicHttpClient client = PublicHttpClient.create(Duration.ofSeconds(1), Duration.ofSeconds(1));

    @Test
    void neverConnectsToALoopbackAddress() {
        assertThatThrownBy(() -> client.send(PublicHttpRequest.create("GET", URI.create("http://127.0.0.1:9/actuator"))))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void neverConnectsToANameThatResolvesInsideTheNetwork() {
        assertThatThrownBy(() -> client.send(PublicHttpRequest.create("POST", URI.create("http://localhost:9/hook"))
                .setBody("{}")))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void neverConnectsToTheCloudMetadataAddress() {
        assertThatThrownBy(() -> client.send(PublicHttpRequest.create("GET",
                URI.create("http://169.254.169.254/latest/meta-data/"))))
                .isInstanceOf(UnknownHostException.class);
    }
}
