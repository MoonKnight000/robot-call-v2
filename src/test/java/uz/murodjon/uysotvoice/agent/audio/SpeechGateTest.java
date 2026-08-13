package uz.murodjon.uysotvoice.agent.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate decides what the (per-second billed) recognizer hears. Both of its margins
 * exist to protect recognition: too little pre-roll and utterances lose their first
 * syllable, too little post-roll and the provider never emits a final — which stalls
 * the whole turn. Those are the properties worth pinning down.
 */
class SpeechGateTest {

    private static final int RATE = 8000;
    /** One 20ms RTP frame at 8 kHz. */
    private static final int FRAME = 160;
    /** The VAD window size configured for 8 kHz (32ms). */
    private static final int WINDOW = 256;

    /** The plain gate: opens on the first speech window, one hangover, no adaptation. */
    private static SpeechGate gate() {
        return new SpeechGate(RATE, 500, 1000, 0, 0, 0);
    }

    /** Short answers (up to 600ms of speech) close after 300ms instead of the full 1000. */
    private static SpeechGate adaptiveGate() {
        return new SpeechGate(RATE, 500, 1000, 0, 600, 300);
    }

    /** Opens only after 100ms of continuous speech — a blip is not an utterance. */
    private static SpeechGate confirmingGate() {
        return new SpeechGate(RATE, 500, 1000, 100, 0, 0);
    }

    /** Feed {@code ms} of speech or silence in VAD-sized windows. */
    private static void feed(SpeechGate gate, boolean speech, int ms) {
        for (int sent = 0; sent < ms * RATE / 1000; sent += WINDOW) {
            gate.onVadWindow(speech, WINDOW);
        }
    }

    private static short[] frame(short value) {
        short[] pcm = new short[FRAME];
        java.util.Arrays.fill(pcm, value);
        return pcm;
    }

    @Test
    void startsClosedSoIdleAudioIsNeverBilled() {
        assertThat(gate().isOpen()).isFalse();
    }

    @Test
    void speechOpensItImmediately() {
        SpeechGate gate = gate();

        gate.onVadWindow(true, WINDOW);

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void staysOpenThroughAPauseShorterThanThePostRoll() {
        // The provider's end-of-utterance detector needs to hear this silence; closing
        // during it means the final transcript never arrives.
        SpeechGate gate = gate();
        gate.onVadWindow(true, WINDOW);

        for (int sent = 0; sent < RATE / 2; sent += WINDOW) { // 500ms of silence
            gate.onVadWindow(false, WINDOW);
        }

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void closesOnceThePostRollHasElapsed() {
        SpeechGate gate = gate();
        gate.onVadWindow(true, WINDOW);

        for (int sent = 0; sent < RATE * 2; sent += WINDOW) { // 2s of silence
            gate.onVadWindow(false, WINDOW);
        }

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void speechRestartsTheHangoverClock() {
        SpeechGate gate = gate();
        gate.onVadWindow(true, WINDOW);
        for (int sent = 0; sent < 900 * RATE / 1000; sent += WINDOW) { // 900ms, just under
            gate.onVadWindow(false, WINDOW);
        }
        gate.onVadWindow(true, WINDOW);
        for (int sent = 0; sent < 900 * RATE / 1000; sent += WINDOW) {
            gate.onVadWindow(false, WINDOW);
        }

        assertThat(gate.isOpen()).isTrue();
    }

    // The confirmation window: Silero scores "is this speech", not "is this the caller",
    // so a voice across the room opens the gate exactly as readily as the person on the
    // phone. What these pin down is that a blip does not become a turn — and that a real
    // answer still does.

    @Test
    void aBlipShorterThanTheConfirmationWindowNeverOpensIt() {
        SpeechGate gate = confirmingGate();

        feed(gate, true, 32);   // one VAD window: someone across the room

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void repeatedBlipsDoNotAddUpToAnOpenGate() {
        // Without resetting the count on silence, background chatter would open the gate
        // eventually however short each individual blip was.
        SpeechGate gate = confirmingGate();

        for (int i = 0; i < 20; i++) {
            feed(gate, true, 32);
            feed(gate, false, 200);
        }

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void aRealAnswerStillOpensIt() {
        // "ha" is the shortest thing a caller actually says, and it must get through.
        SpeechGate gate = confirmingGate();

        feed(gate, true, 200);

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void whatWasSpentConfirmingIsStillReplayed() {
        // The confirmation costs no audio: the run-up sits in the pre-roll ring and is
        // drained when the gate opens, so the recognizer hears the utterance whole.
        SpeechGate gate = confirmingGate();
        gate.buffer(frame((short) 1), FRAME);
        feed(gate, true, 200);

        assertThat(gate.isOpen()).isTrue();
        assertThat(gate.drainPreRoll()).hasSize(FRAME);
    }

    @Test
    void preRollReplaysTheRunUpInOrder() {
        SpeechGate gate = gate();
        gate.buffer(frame((short) 1), FRAME);
        gate.buffer(frame((short) 2), FRAME);

        short[] preRoll = gate.drainPreRoll();

        assertThat(preRoll).hasSize(2 * FRAME);
        assertThat(preRoll[0]).isEqualTo((short) 1);
        assertThat(preRoll[FRAME - 1]).isEqualTo((short) 1);
        assertThat(preRoll[FRAME]).isEqualTo((short) 2);
        assertThat(preRoll[2 * FRAME - 1]).isEqualTo((short) 2);
    }

    @Test
    void preRollKeepsOnlyTheMostRecentAudio() {
        // 20ms of pre-roll fits exactly one frame; the older one has to fall out or the
        // gate would replay minutes of silence at the start of every utterance.
        SpeechGate gate = new SpeechGate(RATE, 20, 1000, 0, 0, 0);
        gate.buffer(frame((short) 1), FRAME);
        gate.buffer(frame((short) 2), FRAME);

        short[] preRoll = gate.drainPreRoll();

        assertThat(preRoll).hasSize(FRAME);
        assertThat(preRoll[0]).isEqualTo((short) 2);
        assertThat(preRoll[FRAME - 1]).isEqualTo((short) 2);
    }

    @Test
    void drainingEmptiesTheBuffer() {
        SpeechGate gate = gate();
        gate.buffer(frame((short) 1), FRAME);

        assertThat(gate.drainPreRoll()).isNotNull();
        assertThat(gate.drainPreRoll()).isNull();
    }

    @Test
    void bypassKeepsEverythingFlowing() {
        // VAD inference failed: recognition must not be degraded to save money.
        SpeechGate gate = gate();
        gate.bypass();

        for (int sent = 0; sent < RATE * 5; sent += WINDOW) {
            gate.onVadWindow(false, WINDOW);
        }

        assertThat(gate.isOpen()).isTrue();
    }

    // Adaptive hangover: with client-side endpointing the gate shutting is what ends the
    // utterance, so what these pin down is who gets cut off early and who never does.

    @Test
    void aShortAnswerIsClosedOnTheShortHangover() {
        SpeechGate gate = adaptiveGate();

        feed(gate, true, 300);   // "ha"
        feed(gate, false, 400);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void aLongUtteranceStillWaitsTheFullHangover() {
        // The case that must not be cut short: a sentence with a pause in it.
        SpeechGate gate = adaptiveGate();

        feed(gate, true, 1500);
        feed(gate, false, 400);

        assertThat(gate.isOpen()).isTrue();

        feed(gate, false, 800);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void everyUtteranceIsJudgedOnItsOwnLength() {
        // A long answer must not leave the next "ha" waiting a second for no reason.
        SpeechGate gate = adaptiveGate();
        feed(gate, true, 1500);
        feed(gate, false, 1200);
        assertThat(gate.isOpen()).isFalse();

        feed(gate, true, 300);
        feed(gate, false, 400);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void withoutAdaptationAShortAnswerWaitsLikeEverythingElse() {
        SpeechGate gate = gate();

        feed(gate, true, 300);
        feed(gate, false, 400);

        assertThat(gate.isOpen()).isTrue();
    }
}
