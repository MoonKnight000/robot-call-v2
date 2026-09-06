package uz.murodjon.robotcallv2.scenario.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhook;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The URL here is written by a customer through the scenario API, so the interesting cases
 * are the ones where it points back at us. Every URL below is rejected before a socket is
 * opened, which is also why this test needs no network: a literal address is parsed rather
 * than resolved, and {@code localhost} comes from the hosts file.
 */
class FactWebhookClientTest {

    private final FactWebhookClient client = new FactWebhookClient();
    private static final FactWebhookRequest CALL =
            FactWebhookRequest.inbound("998901234567", "998712000000");

    @Test
    void refusesLoopback() {
        assertThat(fetch("http://127.0.0.1:8080/actuator/env")).isNull();
        assertThat(fetch("http://localhost:8080/facts")).isNull();
        assertThat(fetch("http://[::1]/facts")).isNull();
    }

    @Test
    void refusesTheCloudMetadataAddress() {
        assertThat(fetch("http://169.254.169.254/latest/meta-data/")).isNull();
    }

    @Test
    void refusesPrivateNetworks() {
        assertThat(fetch("http://10.0.0.5/facts")).isNull();
        assertThat(fetch("http://192.168.1.10/facts")).isNull();
        assertThat(fetch("http://172.16.0.1/facts")).isNull();
    }

    @Test
    void refusesEverySchemeButHttp() {
        assertThat(fetch("ftp://files.example.com/facts.json")).isNull();
        assertThat(fetch("file:///etc/passwd")).isNull();
        assertThat(fetch("gopher://example.com/")).isNull();
    }

    @Test
    void refusesWhatIsNotAUsableUrl() {
        assertThat(fetch("bu url emas")).isNull();
        assertThat(fetch("https://")).isNull();
        assertThat(fetch("")).isNull();
    }

    @Test
    void doesNothingWhenNoWebhookIsConfigured() {
        assertThat(client.fetchFacts(null, CALL)).isNull();
        assertThat(client.fetchFacts(new FactWebhook("https://api.example.com/facts"), null)).isNull();
    }

    private String fetch(String url) {
        return client.fetchFacts(new FactWebhook(url), CALL);
    }
}
