package uz.murodjon.robotcallv2.agent.stt;

/**
 * How eagerly Yandex SpeechKit v3 decides the caller has finished speaking — the
 * end-of-utterance (EOU) detector that emits the final transcript a turn waits on.
 *
 * <p>This is the single largest term in the §1.3 turnaround budget. Nothing here
 * changes <em>what</em> the recognizer hears (same model, same audio, same accuracy) —
 * only <em>when</em> it declares the utterance over.
 */
public enum EouSensitivity {

    /**
     * SpeechKit's conservative detector, and what the server uses when no classifier is
     * configured. Waits out a long pause before committing, so a caller who thinks
     * mid-sentence is never cut off — at the cost of several hundred milliseconds on
     * every single turn.
     */
    DEFAULT,

    /**
     * The fast detector. Emits the final noticeably sooner and may occasionally split an
     * utterance a caller had not finished.
     *
     * <p>Safe here because a premature final does not lose speech: whatever the caller
     * says while the turn is already running is held by
     * {@link uz.murodjon.robotcallv2.agent.dialog.DialogSession#deferInput} and merged
     * into the next turn rather than dropped.
     */
    HIGH
}
