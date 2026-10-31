package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * Where a contact goes once an outcome matched (Retell "Next").
 */
public enum OutcomeExit {
    /** On to another step of the sequence. */
    STEP,
    /** Out of the sequence, without counting as converted. */
    END,
    /** Out of the sequence as a conversion — counted by the campaign's "successful". */
    END_SUCCESSFUL
}
