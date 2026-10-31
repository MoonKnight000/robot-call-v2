package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeMatch;

import java.util.List;

/**
 * A named result a call can reach (Retell "Outcome"), on one step or global to the sequence.
 * Judged only when somebody answered.
 *
 * @param id            unique inside the sequence; what a target records once it reached it
 * @param prompt        PROMPT only: what the call must have been, read by an LLM by meaning
 * @param match         METRICS only: whether all conditions must hold or any one of them
 * @param conditions    METRICS only
 * @param saveToContact written to the contact when the outcome matches
 * @param next          where the contact goes
 */
public record SequenceOutcome(
        String id,
        String name,
        OutcomeEvaluation evaluation,
        String prompt,
        OutcomeMatch match,
        List<OutcomeCondition> conditions,
        OutcomeContactUpdate saveToContact,
        OutcomeRoute next
) {

    public SequenceOutcome {
        evaluation = evaluation != null ? evaluation : OutcomeEvaluation.PROMPT;
        match = match != null ? match : OutcomeMatch.ALL;
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        saveToContact = saveToContact != null ? saveToContact : OutcomeContactUpdate.NONE;
        next = next != null ? next : OutcomeRoute.END;
    }

    /** A PROMPT outcome with nothing written to the contact. */
    public static SequenceOutcome ofPrompt(String id, String name, String prompt, OutcomeRoute next) {
        return new SequenceOutcome(id, name, OutcomeEvaluation.PROMPT, prompt, null, null, null, next);
    }

    /** Retell's three global outcomes every new sequence starts with. */
    public static List<SequenceOutcome> createDefaults() {
        return List.of(
                new SequenceOutcome("do_not_call", "Do Not Call", OutcomeEvaluation.PROMPT,
                        "The lead asked not to be called or contacted again.", null, null,
                        new OutcomeContactUpdate(true, List.of()), OutcomeRoute.END),
                ofPrompt("success", "Success",
                        "The lead agreed to move forward: made a commitment, accepted the offer or booked what was asked.",
                        OutcomeRoute.END_SUCCESSFUL),
                ofPrompt("not_interested", "Not Interested",
                        "The lead said they are not interested and declined to move forward.",
                        OutcomeRoute.END));
    }
}
