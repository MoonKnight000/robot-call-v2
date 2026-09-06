package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiTtsProviderTest {

    @Test
    void providerMetadata() {
        GeminiTtsProperties geminiProps = new GeminiTtsProperties(
                "dummy-key", "https://generativelanguage.googleapis.com/v1beta/models",
                "gemini-3.1-flash-tts-preview", "Aoede", Map.of("uz-UZ", "Aoede", "ru-RU", "Kore"), 24000, 10);
        TtsProperties props = new TtsProperties(true, "gemini", "uz-UZ", null, geminiProps, null, null, null, null);
        GeminiTtsProvider provider = new GeminiTtsProvider(props);

        assertThat(provider.name()).isEqualTo("gemini");
        assertThat(provider.supports("uz-UZ")).isTrue();
        assertThat(provider.supports("ru-RU")).isTrue();
    }

    @Test
    void convertRawPcm24kTo8k() {
        // 240 samples at 24 kHz (10 ms)
        short[] source = new short[240];
        for (int i = 0; i < source.length; i++) {
            source[i] = (short) (Math.sin(2 * Math.PI * 440 * i / 24000.0) * 16000);
        }
        byte[] rawBytes = new byte[source.length * 2];
        ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(source);

        short[] samples = GeminiTtsProvider.toSourceSamples(rawBytes);
        assertThat(samples).isEqualTo(source);
    }

    @Test
    void convertWav24kTo8k() {
        // Create 240 samples at 24 kHz and package in WAV
        short[] source = new short[240];
        for (int i = 0; i < source.length; i++) {
            source[i] = (short) (Math.sin(2 * Math.PI * 440 * i / 24000.0) * 16000);
        }
        byte[] pcmData = new byte[source.length * 2];
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(source);
        byte[] header = WavHeader.bytes(24000, 1, pcmData.length);
        byte[] wavBytes = new byte[header.length + pcmData.length];
        System.arraycopy(header, 0, wavBytes, 0, header.length);
        System.arraycopy(pcmData, 0, wavBytes, header.length, pcmData.length);

        short[] samples = GeminiTtsProvider.toSourceSamples(wavBytes);
        assertThat(samples).isEqualTo(source);
    }

    @Test
    void emptyAudioReturnsEmptyArray() {
        assertThat(GeminiTtsProvider.toSourceSamples(new byte[0])).isEmpty();
        assertThat(GeminiTtsProvider.toSourceSamples(null)).isEmpty();
    }
}
