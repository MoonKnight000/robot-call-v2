package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model says whether it reached the goal; the call's own record decides
 * (UNIVERSAL-OPERATOR-PLAN A.1). Both edges cost: a conversion counted on the model's word
 * ranks the bot that claims success highest, and a goal lost because the caller hung up
 * before the goodbye hides an order that exists.
 */
class GoalOutcomeTest {

    private static final ScenarioDefinition ORDER = scenario(true);
    private static final ScenarioDefinition NO_GOAL = scenario(false);

    private static ScenarioDefinition scenario(boolean orderIdIsTheGoal) {
        return new ScenarioDefinition(
                List.of(new StageDef("ORDER", "Buyurtmani qabul qiling", List.of(), List.of())),
                List.of(), List.of(),
                List.of(new OutcomeField("orderId", "string", "Yaratilgan buyurtma raqami", orderIdIsTheGoal)),
                "Siz buyurtma qabul qiluvchi agentsiz.", List.of(), null);
    }

    private static Map<String, Object> recorded(Object orderId) {
        return Map.of("orderId", orderId);
    }

    @Test
    void aRecordedGoalIsReachedWhateverTheModelClaimed() {
        assertThat(GoalOutcome.settle(Disposition.COMPLETED, ORDER, recorded(812)))
                .isEqualTo(Disposition.GOAL_REACHED);
        assertThat(GoalOutcome.settle(Disposition.GOAL_NOT_REACHED, ORDER, recorded("A-17")))
                .isEqualTo(Disposition.GOAL_REACHED);
    }

    @Test
    void aGoalRecordedBeforeTheCallerHungUpStillCounts() {
        assertThat(GoalOutcome.settle(null, ORDER, recorded("A-17"))).isEqualTo(Disposition.GOAL_REACHED);
        assertThat(GoalOutcome.settle(Disposition.HUNG_UP, ORDER, recorded("A-17")))
                .isEqualTo(Disposition.GOAL_REACHED);
    }

    @Test
    void aClaimedGoalWithNothingRecordedIsNotReached() {
        assertThat(GoalOutcome.settle(Disposition.GOAL_REACHED, ORDER, Map.of()))
                .isEqualTo(Disposition.GOAL_NOT_REACHED);
        assertThat(GoalOutcome.settle(Disposition.COMPLETED, ORDER, recorded(" ")))
                .isEqualTo(Disposition.GOAL_NOT_REACHED);
        assertThat(GoalOutcome.settle(Disposition.GOAL_REACHED, ORDER, recorded(false)))
                .isEqualTo(Disposition.GOAL_NOT_REACHED);
    }

    @Test
    void aCallThatSettledNothingIsLeftForTheTeardown() {
        assertThat(GoalOutcome.settle(null, ORDER, Map.of())).isNull();
        assertThat(GoalOutcome.settle(Disposition.HUNG_UP, ORDER, Map.of())).isEqualTo(Disposition.HUNG_UP);
    }

    @Test
    void anOutcomeWithAConsequenceOfItsOwnIsKept() {
        assertThat(GoalOutcome.settle(Disposition.TRANSFERRED, ORDER, recorded("A-17")))
                .isEqualTo(Disposition.TRANSFERRED);
        assertThat(GoalOutcome.settle(Disposition.DO_NOT_CALL, ORDER, recorded("A-17")))
                .isEqualTo(Disposition.DO_NOT_CALL);
        assertThat(GoalOutcome.settle(Disposition.CALLBACK_REQUESTED, ORDER, recorded("A-17")))
                .isEqualTo(Disposition.CALLBACK_REQUESTED);
    }

    @Test
    void aScenarioWithoutAGoalCannotEndOnOne() {
        assertThat(GoalOutcome.settle(Disposition.GOAL_REACHED, NO_GOAL, recorded("A-17")))
                .isEqualTo(Disposition.COMPLETED);
        assertThat(GoalOutcome.settle(Disposition.PROMISE_TO_PAY, NO_GOAL, Map.of()))
                .isEqualTo(Disposition.PROMISE_TO_PAY);
        assertThat(GoalOutcome.settle(null, NO_GOAL, Map.of())).isNull();
    }
}
