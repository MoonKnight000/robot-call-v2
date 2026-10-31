package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How a {@code SequenceOutcome} decides whether a call matches it (Retell "Description").
 */
public enum OutcomeEvaluation {
    /** An LLM reads the transcript and judges it against the outcome's prompt, by meaning. */
    PROMPT,
    /** Rule-based: the outcome's conditions are compared with the call's variables. */
    METRICS
}
