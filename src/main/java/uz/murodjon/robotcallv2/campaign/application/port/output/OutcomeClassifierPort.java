package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;

import java.util.List;

/**
 * Judges a call's transcript against PROMPT outcomes by meaning (Retell's outcome classifier):
 * all of them in one request, so the model picks the one that fits best.
 */
public interface OutcomeClassifierPort {

    /**
     * @param outcomes PROMPT outcomes only, each with its id and prompt
     * @return the id of the outcome the call reached, or null when none fits, the transcript is
     *         empty or no model could be asked
     */
    String findMatchingOutcomeId(long companyId, String transcript, List<SequenceOutcome> outcomes);
}
