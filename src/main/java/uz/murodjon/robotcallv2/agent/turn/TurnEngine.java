package uz.murodjon.robotcallv2.agent.turn;

/**
 * How a call's end-of-turn verdicts are combined ({@code voice-agent.turn-taking.engine}).
 */
public enum TurnEngine {

    /**
     * Every installed source, as before the setting existed ({@link AutoTurnPolicy}): the LLM's
     * verdict on the words takes the word rules' place, and either an unfinished turn model or
     * unfinished words extend the pause.
     */
    AUTO,

    /** Only the language model's verdict counts. */
    LLM,

    /** Only the turn model (Smart Turn / text model) counts. */
    TURN_MODEL,

    /** Only the word rules count. */
    WORDS,

    /**
     * The sources in a trusted order, per language ({@code turn-taking.mixed-order},
     * {@code turn-taking.mixed-languages}): the first one with a verdict decides
     * ({@link PriorityTurnPolicy}).
     */
    MIXED
}
