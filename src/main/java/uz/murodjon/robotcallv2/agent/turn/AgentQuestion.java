package uz.murodjon.robotcallv2.agent.turn;

/**
 * What the agent's last line asked the caller, as far as it decides how long the caller's
 * answer runs ({@link AgentQuestionCues}) — the conversation context a turn detector reads
 * alongside the caller's own words.
 */
public enum AgentQuestion {
    /** A yes/no or a pick from options ("vaqtingiz bormi?", "necha xonali?"): a few words answer it. */
    CLOSED,
    /** A why or an explanation ("nima sababdan?"): the caller may pause while finding the words. */
    OPEN,
    /** No question, or one the rules cannot read; the caller's words and the timer decide alone. */
    NONE
}
