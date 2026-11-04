package uz.murodjon.robotcallv2.agent.vad;

/**
 * Which voice-activity model scores a call ({@code voice-agent.vad.engine}).
 */
public enum VadEngine {

    /** TEN VAD when it is loaded, Silero otherwise — the behaviour before the setting existed. */
    AUTO,

    /** Silero only. */
    SILERO,

    /** TEN VAD only; falls back to Silero, with a warning, when TEN is not loaded. */
    TEN,

    /**
     * Both models on every window ({@link MixedVadScorer}): speech starts only when both hear it,
     * which keeps a cough or a car from interrupting the bot, and continues while either does, so
     * a soft word mid-sentence does not end the caller's turn. Falls back to whichever is loaded.
     */
    MIXED
}
