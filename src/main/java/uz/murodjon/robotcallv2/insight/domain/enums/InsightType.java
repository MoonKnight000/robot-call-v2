package uz.murodjon.robotcallv2.insight.domain.enums;

/**
 * What a day of calls says is wrong, and therefore what kind of change would fix it
 * (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>Split by the change rather than by the symptom, because the change is what accepting a
 * proposal has to carry out: a caller asking something the agent could not answer is a
 * {@link #KB_GAP} whether the judge flagged it or the operator did.
 */
public enum InsightType {

    /** Callers asked something the agent had no answer for — the knowledge base is missing it. */
    KB_GAP,

    /** The agent said something it should not have. The suggestion is one guardrail sentence. */
    PROMPT_FIX,

    /** A company tool kept answering with an error — its parameters are described wrongly. */
    TOOL_FIX,

    /** A call that went wrong is worth keeping as a test case, so it cannot go wrong unnoticed again. */
    NEW_TEST_CASE,

    /** The recognizer or the synthesizer keeps mangling a word. The suggestion is a pronunciation rule. */
    PRONUNCIATION,

    /** Callers asked for a person and waited too long, or were transferred when they need not have been. */
    HANDOFF_PATTERN
}
