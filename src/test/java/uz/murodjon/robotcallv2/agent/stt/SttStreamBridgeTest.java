package uz.murodjon.robotcallv2.agent.stt;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A provider that is simply down — every reconnect refused — has to reach the failover.
 * Before, only a stream that took audio and stayed silent was counted, so a hard outage
 * reopened the dead vendor once a second for the whole call and the caller went unheard.
 */
class SttStreamBridgeTest {

    private static final class FakeSession implements SttSession {
        final List<byte[]> sent = new ArrayList<>();
        volatile boolean alive;

        FakeSession(boolean alive) {
            this.alive = alive;
        }

        @Override
        public void sendAudio(byte[] pcm16le) {
            sent.add(pcm16le);
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void close() {
            alive = false;
        }
    }

    /** Hands out the scripted sessions in order, then refuses every connection. */
    private static final class FakeProvider implements SttProvider {
        final String name;
        final Deque<FakeSession> script = new ArrayDeque<>();
        int opened;

        FakeProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                      TranscriptListener listener, boolean externalEndpointing) {
            opened++;
            FakeSession next = script.poll();
            if (next == null) {
                throw new IllegalStateException(name + " refused the connection");
            }
            return next;
        }

        @Override
        public int sampleRate() {
            return 8000;
        }
    }

    @Test
    void aProviderThatRefusesEveryReconnectIsFailedOverAndItsBufferedSpeechReplayedOnce() throws Exception {
        FakeProvider primary = new FakeProvider("primary");
        primary.script.add(new FakeSession(false)); // the stream the call starts on is already dead
        FakeProvider backup = new FakeProvider("backup");
        FakeSession backupSession = new FakeSession(true);
        backup.script.add(backupSession);
        SttProviderSelector selector = mock(SttProviderSelector.class);
        when(selector.findFallback(primary)).thenReturn(backup);

        SttStreamBridge bridge = new SttStreamBridge(primary, 8000, 8000, "test-channel", "uz-UZ", List.of(),
                (text, isFinal, confidence) -> { }, null, mock(SttMetrics.class), null, 0, null, List.of(),
                selector, null, SttStreamProperties.DEFAULTS);

        short[] frame = new short[160];
        bridge.onAudio(frame, frame.length); // reconnect refused — first failure
        Thread.sleep(1100);                  // past the reopen cooldown
        bridge.onAudio(frame, frame.length); // refused again — second failure
        Thread.sleep(1100);
        bridge.onAudio(frame, frame.length); // the threshold is reached: over to the backup

        assertThat(backup.opened).isEqualTo(1);
        // Each of the three frames reaches the backup exactly once: all of them from the
        // replay buffer, and the current one not sent a second time after it.
        assertThat(backupSession.sent).hasSize(3);
        bridge.close();
    }

    /** Records the model and hints of every stream it opens; each stream stays alive. */
    private static final class RecordingProvider implements SttProvider {
        final List<String> models = new ArrayList<>();
        final List<List<String>> hints = new ArrayList<>();

        @Override
        public String name() {
            return "rec";
        }

        @Override
        public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                      TranscriptListener listener, boolean externalEndpointing) {
            return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, List.of(), null);
        }

        @Override
        public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                      TranscriptListener listener, boolean externalEndpointing,
                                      List<String> hints, String model) {
            this.models.add(model);
            this.hints.add(hints);
            return new FakeSession(true);
        }

        @Override
        public int sampleRate() {
            return 8000;
        }
    }

    private static SttStreamBridge recordingBridge(RecordingProvider provider) {
        return new SttStreamBridge(provider, 8000, 8000, "test-channel", "uz-UZ", List.of(),
                (text, isFinal, confidence) -> { }, null, mock(SttMetrics.class), null, 0, null,
                List.of("Payme"), List.of("Nido"), null, "agent-model", SttStreamProperties.DEFAULTS);
    }

    @Test
    void theAgentsKeywordsGoInFrontOfTheCallsHints() {
        RecordingProvider provider = new RecordingProvider();

        recordingBridge(provider);

        assertThat(provider.hints.getFirst()).containsExactly("Nido", "Payme");
        assertThat(provider.models.getFirst()).isEqualTo("agent-model");
    }

    @Test
    void aStageThatChangesNothingDoesNotReopenTheStream() {
        RecordingProvider provider = new RecordingProvider();
        SttStreamBridge bridge = recordingBridge(provider);

        boolean reopening = bridge.retune("rec", null, null);
        bridge.onAudio(new short[160], 160);

        assertThat(reopening).isFalse();
        assertThat(provider.models).hasSize(1);
    }

    @Test
    void aStageModelAndKeywordsReopenTheStreamOnTheNextFrameAndLeavingTheStageRestoresTheAgents() {
        RecordingProvider provider = new RecordingProvider();
        SttStreamBridge bridge = recordingBridge(provider);

        assertThat(bridge.retune("rec", "stage-model", List.of("Click"))).isTrue();
        assertThat(provider.models).hasSize(1); // not on the dialog's thread
        bridge.onAudio(new short[160], 160);

        assertThat(provider.models.getLast()).isEqualTo("stage-model");
        assertThat(provider.hints.getLast()).containsExactly("Click", "Payme");

        assertThat(bridge.retune("rec", null, null)).isTrue();
        bridge.onAudio(new short[160], 160);

        assertThat(provider.models.getLast()).isEqualTo("agent-model");
        assertThat(provider.hints.getLast()).containsExactly("Nido", "Payme");
    }

    @Test
    void aStageModelResolvedForAnotherProviderIsIgnored() {
        RecordingProvider provider = new RecordingProvider();
        SttStreamBridge bridge = recordingBridge(provider);

        assertThat(bridge.retune("someone-else", "stage-model", null)).isFalse();
    }
}
