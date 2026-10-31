package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How a conversation step ended for a contact, grouped coarsely enough to route on. Every
 * {@code Disposition} falls into exactly one of these.
 */
public enum SequenceOutcome {
    /** The call reached its goal (a conversion). */
    SUCCESS,
    /** The contact spoke to the agent and declined. */
    NOT_INTERESTED,
    /** The contact asked not to be called again. */
    DO_NOT_CALL,
    /** The attempts were spent without reaching the contact. */
    NO_RESPONSE,
    /** Anything else: transferred, callback requested, wrong number, failed. */
    OTHER
}
