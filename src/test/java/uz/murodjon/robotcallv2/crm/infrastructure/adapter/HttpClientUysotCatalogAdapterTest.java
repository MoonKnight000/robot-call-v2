package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientUysotCatalogAdapterTest {

    @Test
    void retryableStatuses() {
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(429)).isTrue();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(408)).isTrue();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(502)).isTrue();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(503)).isTrue();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(504)).isTrue();
    }

    @Test
    void nonRetryableStatuses() {
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(200)).isFalse();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(400)).isFalse();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(401)).isFalse();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(403)).isFalse();
        assertThat(HttpClientUysotCatalogAdapter.isRetryable(404)).isFalse();
    }
}
