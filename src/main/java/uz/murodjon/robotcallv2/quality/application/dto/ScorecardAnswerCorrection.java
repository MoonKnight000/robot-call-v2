package uz.murodjon.robotcallv2.quality.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;

/** One answer a supervisor settles differently from the judge — {@code answers[].id} of the scorecard. */
public record ScorecardAnswerCorrection(
        @NotNull Long answerId,
        @NotNull CriterionAnswer answer) {
}
