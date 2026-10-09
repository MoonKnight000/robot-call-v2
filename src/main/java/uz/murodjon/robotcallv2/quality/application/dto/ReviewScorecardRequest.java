package uz.murodjon.robotcallv2.quality.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A supervisor's review of an operator call's scorecard ({@code POST /api/call-scorecards/{callId}/review}).
 *
 * @param corrections the answers settled differently from the judge; empty or omitted confirms
 *                    the judge as it was
 */
public record ReviewScorecardRequest(
        @Valid @Size(max = 100) List<ScorecardAnswerCorrection> corrections,
        @Size(max = 2000) String note) {

    public List<ScorecardAnswerCorrection> correctionsOrEmpty() {
        return corrections == null ? List.of() : corrections;
    }
}
