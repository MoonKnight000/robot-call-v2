package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * What a {@code SequenceStep} does to a contact.
 */
public enum SequenceStepType {
    /** Calls the contact with an agent, up to a number of attempts; routes on how the call ended. */
    CONVERSATION,
    /** Texts the contact, then moves on to the next step. */
    SMS,
    /** Holds the contact for a while, then moves on to the next step. */
    WAIT
}
