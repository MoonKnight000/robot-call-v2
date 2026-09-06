package uz.murodjon.robotcallv2.agent.turn;

/**
 * What the words of an utterance say about whether the caller has finished it — read
 * off the recognizer's last interim while the line is silent ({@link TranscriptTurnCues}).
 */
public enum TurnCue {
    /** Reads as a finished sentence: a finite verb, a yes/no, a closing word. */
    COMPLETE,
    /** Reads as mid-sentence: a conjunction, a case-marked noun, a bare number. */
    INCOMPLETE,
    /** The words do not say either way; the timer decides alone. */
    UNKNOWN
}
