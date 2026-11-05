package uz.murodjon.robotcallv2.agent.stt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stream opened while the line rings is only worth anything if the call that answers can
 * use it exactly as if it had opened the stream itself; every other outcome has to close it
 * and leave the call on the path it had before prewarming existed.
 */
class SttPrewarmRegistryTest {

    private static final String CHANNEL = "chan-1";

    private final SttProvider provider = mock(SttProvider.class);
    private final SttSession session = mock(SttSession.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final SttOpenParams params = new SttOpenParams("deepgram", "ru-RU", List.of(), List.of("Ali"), null, true);

    @BeforeEach
    void setUp() {
        // Run the background open on the calling thread so the stream is ready when asserted.
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());
        when(provider.supportsPrewarm()).thenReturn(true);
        when(provider.startStream(any(), any(), any(), anyBoolean(), any(), any())).thenReturn(session);
        when(session.isAlive()).thenReturn(true);
    }

    private SttPrewarmRegistry registry(boolean enabled, int maxPending) {
        return new SttPrewarmRegistry(new SttPrewarmProperties(enabled, maxPending, 10_000), executor);
    }

    @Test
    void streamWithTheSameParametersIsHandedToTheAnsweredCall() {
        SttPrewarmRegistry registry = registry(true, 10);
        registry.prewarm(CHANNEL, provider, params, 30_000);

        PreopenedStt taken = registry.take(CHANNEL, params);

        assertThat(taken).isNotNull();
        assertThat(taken.session()).isSameAs(session);
        verify(session, never()).close();
        // Taken once: a second ask finds nothing.
        assertThat(registry.take(CHANNEL, params)).isNull();
    }

    @Test
    void streamOpenedForOtherParametersIsClosedNotHandedOver() throws IOException {
        SttPrewarmRegistry registry = registry(true, 10);
        registry.prewarm(CHANNEL, provider, params, 30_000);

        SttOpenParams other = new SttOpenParams("deepgram", "uz-UZ", List.of(), List.of("Ali"), null, true);

        assertThat(registry.take(CHANNEL, other)).isNull();
        verify(session).close();
    }

    @Test
    void streamThatDiedWhileTheLineRangIsClosedNotHandedOver() throws IOException {
        when(session.isAlive()).thenReturn(false);
        SttPrewarmRegistry registry = registry(true, 10);
        registry.prewarm(CHANNEL, provider, params, 30_000);

        assertThat(registry.take(CHANNEL, params)).isNull();
        verify(session).close();
    }

    @Test
    void nothingIsOpenedWhenDisabledOrTheProviderKeepsNoIdleStream() {
        registry(false, 10).prewarm(CHANNEL, provider, params, 30_000);
        when(provider.supportsPrewarm()).thenReturn(false);
        registry(true, 10).prewarm(CHANNEL, provider, params, 30_000);

        verify(provider, never()).startStream(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void callsOverTheCapOpenTheirOwnStreamAtAnswerTime() {
        SttPrewarmRegistry registry = registry(true, 1);
        registry.prewarm("first", provider, params, 30_000);
        registry.prewarm("second", provider, params, 30_000);

        assertThat(registry.take("second", params)).isNull();
        assertThat(registry.take("first", params)).isNotNull();
    }

    @Test
    void transcriptsReachTheBridgeOnlyAfterItBindsToTheStream() {
        SttPrewarmRegistry registry = registry(true, 10);
        registry.prewarm(CHANNEL, provider, params, 30_000);
        ArgumentCaptor<TranscriptListener> opened = ArgumentCaptor.forClass(TranscriptListener.class);
        verify(provider).startStream(any(), any(), opened.capture(), anyBoolean(), any(), any());

        TranscriptListener bridge = mock(TranscriptListener.class);
        opened.getValue().onTranscript("too early", false, 0f);
        PreopenedStt taken = registry.take(CHANNEL, params);
        taken.forwarder().bind(bridge);
        opened.getValue().onTranscript("salom", true, 0.9f);

        verify(bridge, never()).onTranscript("too early", false, 0f);
        verify(bridge).onTranscript("salom", true, 0.9f);
    }

    @Test
    void unclaimedStreamsOfUnansweredCallsAreClosedBySweep() throws IOException {
        SttPrewarmRegistry registry = new SttPrewarmRegistry(new SttPrewarmProperties(true, 10, 0), executor);
        registry.prewarm(CHANNEL, provider, params, 0);

        registry.sweepExpired();

        verify(session).close();
        assertThat(registry.take(CHANNEL, params)).isNull();
    }
}
