package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.TtsMetrics;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The cache key names the primary provider and its voice, so audio from a fallback must
 * never be filed under it — it would be served as that voice to every later call.
 */
class TtsRouterTest {

    private static final String TEXT = "Assalomu alaykum";

    private TtsProviderSelector selector;
    private TtsCache cache;
    private TtsProvider primary;
    private TtsProvider fallback;
    private TtsRouter router;

    @BeforeEach
    void setUp() {
        selector = mock(TtsProviderSelector.class);
        cache = mock(TtsCache.class);
        primary = mock(TtsProvider.class);
        fallback = mock(TtsProvider.class);
        when(primary.name()).thenReturn("primary");
        when(fallback.name()).thenReturn("fallback");
        when(selector.findForCall(any())).thenReturn(primary);
        when(selector.findFallback(primary)).thenReturn(fallback);
        TtsProperties ttsProperties = mock(TtsProperties.class);
        when(ttsProperties.defaultLanguage()).thenReturn("uz-UZ");
        router = new TtsRouter(selector, ttsProperties, mock(TtsMetrics.class), cache, mock(TtsVoiceUseCase.class));
    }

    @Test
    void aLineThePrimarySpeaksIsCached() {
        short[] pcm = {1, 2, 3};
        when(primary.synthesize(anyString(), anyString(), any(), any())).thenReturn(pcm);

        assertThat(router.synthesize(TEXT, "uz-UZ")).isEqualTo(pcm);
        verify(cache).put(eq("primary"), anyString(), any(), anyString(), eq(pcm), any());
    }

    @Test
    void aFallbackLineIsSpokenButNeverCached() {
        short[] pcm = {4, 5, 6};
        when(primary.synthesize(anyString(), anyString(), any(), any())).thenThrow(new RuntimeException("down"));
        when(fallback.synthesize(anyString(), anyString(), any(), any())).thenReturn(pcm);

        assertThat(router.synthesize(TEXT, "uz-UZ")).isEqualTo(pcm);
        verify(cache, never()).put(anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void aStreamThatDiesMidSentenceIsFinishedByTheFallbackAndNeverCached() {
        doAnswer(invocation -> {
            PcmChunkListener onChunk = invocation.getArgument(4);
            onChunk.onChunk(new short[]{9});
            throw new RuntimeException("stream dropped");
        }).when(primary).synthesizeStreaming(anyString(), anyString(), any(), any(), any());
        short[] whole = {1, 2};
        when(fallback.synthesize(anyString(), anyString(), any(), any())).thenReturn(whole);
        List<short[]> heard = new ArrayList<>();

        router.synthesizeStreaming(TEXT, "uz-UZ", null, EffectiveVoiceSettings.NONE, List.of(), heard::add);

        assertThat(heard).hasSize(2);
        assertThat(heard.get(1)).isEqualTo(whole);
        verify(cache, never()).put(anyString(), anyString(), any(), anyString(), any(), any());
    }
}
