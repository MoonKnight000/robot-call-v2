package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AishaTtsProviderTest {

    @Test
    void supportsUzbekRussianAndEnglish() {
        AishaTtsProperties props = new AishaTtsProperties(
                "test-api-key",
                "wss://back.aisha.group/api/v1/tts/realtime",
                "neutral",
                Map.of(),
                java.util.List.of(),
                5000,
                5
        );
        TtsProperties ttsProps = new TtsProperties(true, "aisha", "uz-UZ", null,
                null, null, null, props, null, null, null);
        AishaTtsProvider provider = new AishaTtsProvider(ttsProps, new ObjectMapper());

        assertThat(provider.name()).isEqualTo("aisha");
        assertThat(provider.supports("uz-UZ")).isTrue();
        assertThat(provider.supports("ru-RU")).isTrue();
        assertThat(provider.supports("en-US")).isTrue();
        assertThat(provider.supports("de-DE")).isFalse();
        assertThat(provider.supports(null)).isFalse();
    }

    @Test
    void defaultPoolSizeIsFiveWhenZeroOrNegative() {
        AishaTtsProperties zeroPool = new AishaTtsProperties(
                "key", "url", "speaker", Map.of(), java.util.List.of(), 5000, 0
        );
        assertThat(zeroPool.poolSize()).isEqualTo(5);

        AishaTtsProperties negativePool = new AishaTtsProperties(
                "key", "url", "speaker", Map.of(), java.util.List.of(), 5000, -2
        );
        assertThat(negativePool.poolSize()).isEqualTo(5);

        AishaTtsProperties explicitPool = new AishaTtsProperties(
                "key", "url", "speaker", Map.of(), java.util.List.of(), 5000, 10
        );
        assertThat(explicitPool.poolSize()).isEqualTo(10);
    }
}
