package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTalkMetrics;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TalkActivityTrackerTest {

    private static final long CALL_ID = 42L;
    private static final long STEP_NANOS = TalkActivityTracker.STEP_MS * 1_000_000L;
    private static final short[] SPEECH = frame(2000);
    private static final short[] QUIET = frame(50);

    private final TalkActivityTracker tracker = new TalkActivityTracker(0);

    @Test
    void turnsTakenInOrderShareTheCallWithoutOvertalk() {
        client(0, 50, SPEECH);
        operator(50, 100, SPEECH);

        CallTalkMetrics metrics = tracker.summarize(CALL_ID);

        assertThat(metrics).isEqualTo(new CallTalkMetrics(CALL_ID, 2000, 1000, 1000, 0, 0, 0));
    }

    @Test
    void bothSpeakingAtOnceIsOvertalk() {
        client(0, 50, SPEECH);
        operator(40, 90, SPEECH);

        assertThat(tracker.summarize(CALL_ID).overtalkMs()).isEqualTo(200);
    }

    @Test
    void aBreathBetweenWordsIsStillTalking() {
        client(0, 10, SPEECH);
        client(10, 15, QUIET);
        client(15, 25, SPEECH);

        CallTalkMetrics metrics = tracker.summarize(CALL_ID);

        assertThat(metrics.clientTalkMs()).isEqualTo(500);
        assertThat(metrics.silenceMs()).isZero();
    }

    @Test
    void aPauseNobodyFillsIsSilence() {
        client(0, 10, SPEECH);
        client(10, 100, QUIET);
        operator(100, 110, SPEECH);

        CallTalkMetrics metrics = tracker.summarize(CALL_ID);

        assertThat(metrics.silenceMs()).isEqualTo(1800);
        assertThat(metrics.longestSilenceMs()).isEqualTo(1800);
    }

    @Test
    void noAudioMeansNoMetrics() {
        assertThat(tracker.summarize(CALL_ID)).isNull();
    }

    private void client(int fromStep, int toStep, short[] pcm) {
        for (int step = fromStep; step < toStep; step++) {
            tracker.onClientAudio(pcm, pcm.length, step * STEP_NANOS);
        }
    }

    private void operator(int fromStep, int toStep, short[] pcm) {
        for (int step = fromStep; step < toStep; step++) {
            tracker.onOperatorAudio(pcm, pcm.length, step * STEP_NANOS);
        }
    }

    private static short[] frame(int level) {
        short[] pcm = new short[160];
        Arrays.fill(pcm, (short) level);
        return pcm;
    }
}
