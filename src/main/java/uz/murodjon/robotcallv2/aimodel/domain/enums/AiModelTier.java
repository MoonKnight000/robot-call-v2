package uz.murodjon.robotcallv2.aimodel.domain.enums;

/**
 * What a chat model is for, so a picker can group the catalog the way an operator chooses:
 * by the job, not by the vendor's naming.
 */
public enum AiModelTier {

    /** Short, cheap answers; the lowest latency. */
    FAST,

    /** The everyday choice: price and reasoning in balance. */
    BALANCED,

    /** Hard conversations; slower and dearer. */
    SMART
}
