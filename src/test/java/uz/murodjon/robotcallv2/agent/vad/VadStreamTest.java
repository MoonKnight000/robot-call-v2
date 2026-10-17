package uz.murodjon.robotcallv2.agent.vad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.audio.SpeechGate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VadStreamTest {

    private static final int RATE = 8000;
    private static final int WINDOW = 256; // 32ms
    private static final int MIN_SPEECH_MS = 160; // 5 windows

    private SileroVad vad;
    private VadProperties properties;

    @BeforeEach
    void setUp() {
        vad = mock(SileroVad.class);
        when(vad.newState()).thenReturn(new float[2][1][128]);
        when(vad.newContext()).thenReturn(new float[64]);

        properties = new VadProperties(
                true, "dummy.onnx", RATE, WINDOW,
                0.5f, MIN_SPEECH_MS, 250, null,
                0.35f, 150.0, 2.5,
                0.15, 300.0
        );
    }

    private void feedAudio(VadStream stream, short sampleValue, int windowCount) {
        short[] buffer = new short[WINDOW];
        java.util.Arrays.fill(buffer, sampleValue);
        for (int i = 0; i < windowCount; i++) {
            stream.onAudio(buffer, buffer.length);
        }
    }

    @Test
    @DisplayName("Barge-in triggers when speech energy is above noise floor and bot is quiet")
    void bargeInTriggeredWhenSpeechExceedsMinSpeechWindows() {
        when(vad.run(any(), any(), any())).thenReturn(0.85f);
        AtomicBoolean bargedIn = new AtomicBoolean(false);
        DoubleSupplier botLevel = () -> 0.0;

        VadStream stream = new VadStream(vad, properties, "ch-1",
                () -> {
                    bargedIn.set(true);
                    return true;
                },
                null, null, null, botLevel);

        // Feed 6 windows of speech at amplitude 500 (RMS = 500 > 150)
        feedAudio(stream, (short) 500, 6);

        assertThat(bargedIn.get()).isTrue();
    }

    @Test
    @DisplayName("Acoustic echo below bot output level * ratio is suppressed and does not trigger barge-in")
    void acousticEchoFromBotDoesNotTriggerBargeIn() {
        // Model detects speech (the bot's own voice echoed through speaker)
        when(vad.run(any(), any(), any())).thenReturn(0.95f);
        AtomicBoolean bargedIn = new AtomicBoolean(false);

        // Bot is loud on the wire (RMS = 2000)
        // Echo gate ratio = 0.15 -> echo floor = 300.0 RMS
        DoubleSupplier botLevel = () -> 2000.0;

        VadStream stream = new VadStream(vad, properties, "ch-2",
                () -> {
                    bargedIn.set(true);
                    return true;
                },
                null, null, null, botLevel);

        // Microphone captures speaker echo at amplitude 200 (RMS = 200 < 300)
        feedAudio(stream, (short) 200, 10);

        assertThat(bargedIn.get()).isFalse();
    }

    @Test
    @DisplayName("Caller talking loudly over bot exceeds echo threshold and triggers barge-in")
    void callerTalkingOverBotTriggersBargeInWhenLouderThanEcho() {
        when(vad.run(any(), any(), any())).thenReturn(0.95f);
        AtomicBoolean bargedIn = new AtomicBoolean(false);

        // Bot output level RMS = 2000 -> echo threshold = 300 RMS
        DoubleSupplier botLevel = () -> 2000.0;

        VadStream stream = new VadStream(vad, properties, "ch-3",
                () -> {
                    bargedIn.set(true);
                    return true;
                },
                null, null, null, botLevel);

        // Caller speaks over bot with loud voice: amplitude 800 (RMS = 800 > 300)
        feedAudio(stream, (short) 800, 6);

        assertThat(bargedIn.get()).isTrue();
    }

    @Test
    @DisplayName("Echo suppression prevents false speech windows reaching speech gate")
    void echoSuppressionProtectsSpeechGate() {
        when(vad.run(any(), any(), any())).thenReturn(0.90f);
        DoubleSupplier botLevel = () -> 2000.0;
        SpeechGate gate = new SpeechGate(RATE, 500, 1000, 0, 0, 0, 0, 0d, 0);

        VadStream stream = new VadStream(vad, properties, "ch-4",
                () -> false,
                gate, null, null, botLevel);

        // Echo frames at amplitude 200
        feedAudio(stream, (short) 200, 5);

        // Gate should stay closed because frames were identified as echo
        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    @DisplayName("Speech that goes on after the barge-in is reported once, when it reaches sustainedSpeechMs")
    void sustainedSpeechAfterBargeInIsReportedOnce() {
        when(vad.run(any(), any(), any())).thenReturn(0.85f);
        AtomicInteger sustained = new AtomicInteger();
        VadStream stream = new VadStream(vad, withSustainedSpeechMs(320), "ch-5",
                () -> true, null, null, null, () -> 0.0, sustained::incrementAndGet);

        // 5 windows confirm the barge-in, 10 more (320 ms) are the sustained run.
        feedAudio(stream, (short) 500, 14);
        assertThat(sustained.get()).isZero();
        feedAudio(stream, (short) 500, 1);
        assertThat(sustained.get()).isEqualTo(1);
        feedAudio(stream, (short) 500, 20);
        assertThat(sustained.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("A short sound over the bot ends before sustainedSpeechMs and is not reported")
    void shortSpeechAfterBargeInIsNotReported() {
        when(vad.run(any(), any(), any())).thenReturn(0.85f);
        AtomicInteger sustained = new AtomicInteger();
        VadStream stream = new VadStream(vad, withSustainedSpeechMs(320), "ch-6",
                () -> true, null, null, null, () -> 0.0, sustained::incrementAndGet);

        feedAudio(stream, (short) 500, 8);
        feedAudio(stream, (short) 0, 10);
        assertThat(sustained.get()).isZero();
    }

    private VadProperties withSustainedSpeechMs(int sustainedSpeechMs) {
        return new VadProperties(true, "dummy.onnx", RATE, WINDOW, 0.5f, MIN_SPEECH_MS, 250, null,
                0.35f, 150.0, 2.5, 0.15, 300.0, sustainedSpeechMs);
    }
}
