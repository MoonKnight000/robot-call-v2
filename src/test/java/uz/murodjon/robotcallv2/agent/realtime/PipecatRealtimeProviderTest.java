package uz.murodjon.robotcallv2.agent.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipecatRealtimeProviderTest {

    private RealtimeProperties properties;
    private PipecatRealtimeProvider provider;

    @BeforeEach
    void setUp() {
        PipecatRealtimeProperties pipecatProps = new PipecatRealtimeProperties(
                "test-api-key",
                "https://api.pipecat.daily.co/v1/public",
                null,
                "phone-agent",
                "claude-3-5-haiku-20241022",
                16000,
                15
        );
        properties = new RealtimeProperties(
                true,
                "pipecat",
                true,
                null,
                null,
                null,
                null,
                pipecatProps
        );
        provider = new PipecatRealtimeProvider(properties);
        provider.init();
    }

    @Test
    void reportsExpectedName() {
        assertThat(provider.name()).isEqualTo("pipecat");
    }

    @Test
    void supportsAnyLanguage() {
        assertThat(provider.supports("uz-UZ")).isTrue();
        assertThat(provider.supports("ru-RU")).isTrue();
        assertThat(provider.supports("en-US")).isTrue();
    }

    @Test
    void returnsConfiguredSampleRates() {
        assertThat(provider.inputSampleRate()).isEqualTo(16000);
        assertThat(provider.outputSampleRate()).isEqualTo(16000);
    }

    @Test
    void supportsSubEngineConfigurationInCallConfig() {
        RealtimeCallConfig config = new RealtimeCallConfig(
                "chan-1",
                "uz-UZ",
                "Prompt",
                "voice-1",
                List.of(),
                "deepgram",
                "claude-3-5-haiku",
                "cartesia",
                null
        );
        assertThat(config.pipecatStt()).isEqualTo("deepgram");
        assertThat(config.pipecatLlm()).isEqualTo("claude-3-5-haiku");
        assertThat(config.pipecatTts()).isEqualTo("cartesia");
        // No scenario model, so the company's sub-engine is what the provider will send.
        assertThat(config.modelOr(config.pipecatLlm())).isEqualTo("claude-3-5-haiku");
    }
}
