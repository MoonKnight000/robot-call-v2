package uz.murodjon.uysotvoice.agent.dialog;

/** What became of one piece of speech handed to {@link SpeechOutput#speak}. */
public enum SpeechOutcome {

    /** Audio was synthesized and queued for the caller. */
    SPOKEN,

    /** The fact guard refused it — it stated a figure that is not in the facts. */
    BLOCKED,

    /** Nothing to say, TTS is off, TTS failed, or a barge-in overtook it. */
    SKIPPED
}
