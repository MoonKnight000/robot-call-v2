package uz.murodjon.robotcallv2.agent.stt;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeminiSttProviderTest {

    @Test
    void providerMetadata() {
        GeminiSttProperties geminiProps = new GeminiSttProperties(
                "dummy-key", "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
                "gemini-3.5-transcribe", 16000, 10);
        SttProperties props = new SttProperties(
                true, "gemini", "uz-UZ", List.of(), null, null, 0,
                geminiProps, null, null, null, null);
        VoiceMetrics metrics = mock(VoiceMetrics.class);
        GeminiSttProvider provider = new GeminiSttProvider(props, metrics);

        assertThat(provider.name()).isEqualTo("gemini");
        assertThat(provider.sampleRate()).isEqualTo(16000);
    }
}
