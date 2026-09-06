package uz.murodjon.robotcallv2.agent.audio;

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
        return new SpeechGate(RATE, 500, 1000, 0, 0, 0, 0, 0d, 0);
    }

    /** Short answers (up to 600ms of speech) close after 300ms instead of the full 1000. */
    private static SpeechGate adaptiveGate() {
        return new SpeechGate(RATE, 500, 1000, 0, 600, 300, 0, 0d, 0);
    }

    /** Opens only after 100ms of continuous speech — a blip is not an utterance. */
    private static SpeechGate confirmingGate() {
        return new SpeechGate(RATE, 500, 1000, 100, 0, 0, 0, 0d, 0);
    }

    /**
     * Learns the long-utterance wait between 1000ms and a 600ms floor, moving half the
     * remaining distance per utterance, and counting speech within 900ms of a close as
     * evidence the close was premature.
     */
    private static SpeechGate learningGate() {
        return new SpeechGate(RATE, 500, 1000, 0, 0, 0, 600, 0.5d, 900);
    }

    /** One turn that ends cleanly: a little speech, then silence nobody breaks. */
    private static void cleanTurn(SpeechGate gate) {
        feed(gate, true, 300);
        feed(gate, false, 2000);
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
        SpeechGate gate = new SpeechGate(RATE, 20, 1000, 0, 0, 0, 0, 0d, 0);
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

    // The learned hangover. post-roll-ms has to be set for the slowest caller on the
    // list, and every brisk caller then pays that wait on every turn. What these pin down
    // is that the evidence works both ways: turns that end cleanly earn a shorter wait,
    // and one caller talking through a close takes it straight back.

    @Test
    void theWaitStartsAtTheConfiguredMaximum() {
        // Nothing has been learned yet, so the first utterance is treated as carefully as
        // it would be without any of this.
        assertThat(learningGate().hangoverMs()).isEqualTo(1000);
    }

    @Test
    void turnsThatEndCleanlyShortenTheWait() {
        SpeechGate gate = learningGate();

        cleanTurn(gate);
        assertThat(gate.hangoverMs()).isEqualTo(800);

        cleanTurn(gate);
        assertThat(gate.hangoverMs()).isEqualTo(700);
    }

    @Test
    void speakingThroughACloseTakesTheWaitBack() {
        // The failure this exists to undo: the gate shut and the caller was still talking,
        // so their next words arrive as a separate turn.
        SpeechGate gate = learningGate();
        cleanTurn(gate);
        assertThat(gate.hangoverMs()).isEqualTo(800);

        feed(gate, true, 300);
        feed(gate, false, 1000);   // closes after the learned 800ms, 200ms left over
        feed(gate, true, 100);     // and the caller carries on, well inside the grace

        assertThat(gate.hangoverMs()).isEqualTo(900);
    }

    @Test
    void theWaitNeverFallsThroughTheFloor() {
        // Below the floor the recognizer stops hearing enough silence to be sure the
        // caller stopped at all, however brisk they are.
        SpeechGate gate = learningGate();

        for (int turn = 0; turn < 50; turn++) {
            cleanTurn(gate);
        }

        assertThat(gate.hangoverMs()).isEqualTo(600);
    }

    @Test
    void speechLongAfterACloseIsJustTheNextTurn() {
        // A caller answering the NEXT question is not a caller who never finished the
        // last one; counting it as one would leave the wait pinned at the maximum.
        SpeechGate gate = learningGate();
        cleanTurn(gate);

        feed(gate, true, 300);
        feed(gate, false, 3000);   // closes, and the grace runs out in the silence
        feed(gate, true, 300);

        assertThat(gate.hangoverMs()).isEqualTo(700);
    }

    @Test
    void withoutTheFloorNothingIsLearned() {
        // A floor at or above post-roll-ms leaves nothing to learn, so the gate stays on
        // the fixed wait rather than pretending to adapt.
        SpeechGate gate = new SpeechGate(RATE, 500, 1000, 0, 0, 0, 1000, 0.5d, 900);

        cleanTurn(gate);

        assertThat(gate.hangoverMs()).isEqualTo(1000);
    }

    // The semantic second opinion (SmartTurnDetector). It may only ever ADD silence: the
    // timer's wait is the floor, because a model deciding to cut a wait short is a model
    // deciding when a caller gets interrupted.

    @Test
    void anUtteranceThatSoundsUnfinishedGetsMoreSilence() {
        SpeechGate gate = gate();                       // closes after 1000ms
        gate.setTurnDetector(() -> false, 500);

        feed(gate, true, 300);
        feed(gate, false, 1200);
        assertThat(gate.isOpen()).isTrue();             // 1500ms is the new bar

        feed(gate, false, 400);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void anUtteranceThatSoundsFinishedClosesOnTheTimerAlone() {
        SpeechGate gate = gate();
        gate.setTurnDetector(() -> true, 500);

        feed(gate, true, 300);
        feed(gate, false, 1200);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void theModelIsAskedOncePerUtterance() {
        // It costs milliseconds and the answer cannot change while the line stays silent,
        // so scoring every 32ms window afterwards would buy nothing.
        int[] asked = {0};
        SpeechGate gate = gate();
        gate.setTurnDetector(() -> {
            asked[0]++;
            return false;
        }, 500);

        feed(gate, true, 300);
        feed(gate, false, 2000);

        assertThat(asked[0]).isEqualTo(1);
    }

    @Test
    void speakingAgainPutsTheQuestionBack() {
        // The verdict was about the pause before those words; the pause after them is a
        // different question.
        int[] asked = {0};
        SpeechGate gate = gate();
        gate.setTurnDetector(() -> {
            asked[0]++;
            return false;
        }, 500);

        feed(gate, true, 300);
        feed(gate, false, 1100);   // past the timer, into the extension
        feed(gate, true, 300);     // ...and the caller carries on
        feed(gate, false, 1100);

        assertThat(asked[0]).isEqualTo(2);
    }

    @Test
    void withNoModelNothingIsExtended() {
        SpeechGate gate = gate();

        feed(gate, true, 300);
        feed(gate, false, 1200);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void aShortAnswerIsNotWhatTheAdaptationMoves() {
        // Short answers are already on the aggressive path; the learned wait belongs to
        // the utterances that can still be mid-sentence.
        SpeechGate gate = new SpeechGate(RATE, 500, 1000, 0, 600, 300, 600, 0.5d, 900);
        cleanTurn(gate);

        feed(gate, true, 300);   // "ha"
        feed(gate, false, 400);

        assertThat(gate.isOpen()).isFalse();
    }

    // ---- what the words say (TranscriptTurnCues) --------------------------------------

    @Test
    void aFinishedSoundingSentenceClosesOnTheCompleteWait() {
        SpeechGate gate = gate();                       // 1000ms for everyone else
        gate.setTranscriptCues(600);

        feed(gate, true, 2000);                         // a long answer, not a short one
        gate.onInterimTranscript("keyingi oyning beshinchisida to'layman");
        feed(gate, false, 700);

        assertThat(gate.isOpen()).isFalse();
        assertThat(gate.lastCloseWaitMs()).isBetween(600, 700);
    }

    @Test
    void anUnfinishedSoundingSentenceWaitsOutTheExtensionToo() {
        SpeechGate gate = gate();
        gate.setTranscriptCues(600);

        feed(gate, true, 2000);
        gate.onInterimTranscript("men pulni bankga");
        feed(gate, false, 1200);
        assertThat(gate.isOpen()).isTrue();             // 1000 + the 400ms default extension

        feed(gate, false, 300);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void aNeutralEndingKeepsTheTimer() {
        SpeechGate gate = gate();
        gate.setTranscriptCues(600);

        feed(gate, true, 2000);
        gate.onInterimTranscript("shartnoma raqami");
        feed(gate, false, 900);
        assertThat(gate.isOpen()).isTrue();

        feed(gate, false, 200);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void theLatestInterimReplacesTheVerdictOfTheOneBefore() {
        // "to'layman" read as finished; "to'layman lekin" says the caller was not.
        SpeechGate gate = gate();
        gate.setTranscriptCues(600);

        feed(gate, true, 2000);
        gate.onInterimTranscript("to'layman");
        gate.onInterimTranscript("to'layman lekin");
        feed(gate, false, 1200);

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void withoutACompleteWaitTheWordsCannotShortenAnything() {
        SpeechGate gate = gate();                       // setTranscriptCues never called

        feed(gate, true, 2000);
        gate.onInterimTranscript("to'layman");
        feed(gate, false, 900);

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void aOneWordAnswerTakesTheShortWaitEvenWhenItWasSpokenSlowly() {
        SpeechGate gate = adaptiveGate();               // short answers close at 300ms
        gate.setTranscriptCues(600);

        feed(gate, true, 900);                          // longer than the 600ms short-utterance bar
        gate.onInterimTranscript("yo'q");
        feed(gate, false, 350);

        assertThat(gate.isOpen()).isFalse();
    }
}
