package uz.murodjon.robotcallv2.quality.application.port.output;

import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardVerdict;

import java.util.List;

/** The model that reads an operator call's transcript and answers each criterion. */
public interface ScorecardJudgePort {

    /**
     * One answer per criterion, in the criteria's order, with how the client came across;
     * null when the judge is off, the call was not sampled, or the model failed.
     *
     * @param transcript the call, one {@code ROLE: text} line per utterance
     */
    ScorecardVerdict findVerdict(List<ScorecardCriterion> criteria, String transcript);
}
