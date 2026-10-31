package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.util.List;

/**
 * A CONVERSATION step's outcomes: its own, checked before the sequence's global ones, and the
 * two every step has built in.
 *
 * @param other      where a contact goes when somebody answered and no outcome matched
 * @param noResponse where it goes when the step's last attempt went unanswered
 */
public record StepOutcomes(List<SequenceOutcome> outcomes, OutcomeRoute other, OutcomeRoute noResponse) {

    public static final StepOutcomes NONE = new StepOutcomes(List.of(), OutcomeRoute.END, OutcomeRoute.END);

    public StepOutcomes {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        other = other != null ? other : OutcomeRoute.END;
        noResponse = noResponse != null ? noResponse : OutcomeRoute.END;
    }
}
