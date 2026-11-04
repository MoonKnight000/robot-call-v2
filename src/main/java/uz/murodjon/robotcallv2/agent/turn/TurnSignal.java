package uz.murodjon.robotcallv2.agent.turn;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;

/**
 * One source of an end-of-turn opinion — Smart Turn from the audio, the text model from the
 * words — opened per call and asked about each pause. How opinions are combined is not the
 * signal's business; it only says what it hears.
 *
 * <p>Asked on the thread that feeds the call's speech gate (the RTP consumer).
 */
public interface TurnSignal {

    /** The signal's name for logs, metrics and the turn dataset: {@code smart-turn}, {@code text-turn}. */
    String name();

    /** What the signal makes of the pause in {@code moment}. */
    TurnOpinion judge(TurnMoment moment);

    /** The call audio this signal needs to hear, or null when it judges the words only. */
    default AudioListener audioListener() {
        return null;
    }
}
