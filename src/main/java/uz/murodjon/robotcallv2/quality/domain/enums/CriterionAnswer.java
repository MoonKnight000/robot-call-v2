package uz.murodjon.robotcallv2.quality.domain.enums;

/** How a call met one scorecard criterion, as the judge read the transcript. */
public enum CriterionAnswer {
    YES,
    NO,
    /** The criterion did not come up on this call — counts neither for nor against it. */
    NOT_APPLICABLE
}
