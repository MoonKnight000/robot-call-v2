package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StageBranchEvaluatorTest {

    @Test
    void aMissingFactTakesTheMissingBranch() {
        List<StageBranch> branches = List.of(new StageBranch("birthYear", BranchOperator.MISSING, null, "DEBT_NOTICE"));

        assertThat(StageBranchEvaluator.findTarget(branches, Map.<String, Object>of()::get)).isEqualTo("DEBT_NOTICE");
        assertThat(StageBranchEvaluator.findTarget(branches, Map.<String, Object>of("birthYear", 1990)::get)).isNull();
    }

    @Test
    void numbersCompareAsNumbers() {
        List<StageBranch> branches = List.of(new StageBranch("debtAmount", BranchOperator.LESS_OR_EQUAL, "0", "CLOSING"));

        assertThat(StageBranchEvaluator.findTarget(branches, Map.<String, Object>of("debtAmount", "0.00")::get))
                .isEqualTo("CLOSING");
        // As text "1500000" < "9" would hold; as numbers it does not.
        assertThat(StageBranchEvaluator.findTarget(
                List.of(new StageBranch("debtAmount", BranchOperator.LESS, "9", "CLOSING")),
                Map.<String, Object>of("debtAmount", 1_500_000)::get)).isNull();
    }

    @Test
    void textComparesIgnoringCaseAndTheFirstMatchWins() {
        List<StageBranch> branches = List.of(
                new StageBranch("segment", BranchOperator.EQUALS, "vip", "VIP_OFFER"),
                new StageBranch("segment", BranchOperator.EXISTS, null, "STANDARD_OFFER"));

        assertThat(StageBranchEvaluator.findTarget(branches, Map.<String, Object>of("segment", "VIP")::get))
                .isEqualTo("VIP_OFFER");
        assertThat(StageBranchEvaluator.findTarget(branches, Map.<String, Object>of("segment", "basic")::get))
                .isEqualTo("STANDARD_OFFER");
    }
}
