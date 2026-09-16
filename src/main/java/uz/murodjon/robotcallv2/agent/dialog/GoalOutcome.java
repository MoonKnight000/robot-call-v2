package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Collection;
import java.util.Map;

/**
 * Settles a call's disposition against its scenario's goal (UNIVERSAL-OPERATOR-PLAN A.1).
 *
 * <p>The model picks the disposition it ends a call with, and a model will say it reached
 * the goal. Whether it did is decided here, from what the call recorded: a scenario that
 * names a goal field ({@code OutcomeField.goal}) ends {@link Disposition#GOAL_REACHED} only
 * when that field holds a value — a scenario tool's argument, or a company tool's mapped
 * answer ({@code orderId} from {@code create_order}) — whatever the model claimed.
 *
 * <p>Only the dispositions that describe how the conversation went are open to it. One
 * that carries an action of its own — a transfer, a do-not-call entry, a retry, the debt
 * tools' promise or refusal — is kept as it was set.
 */
final class GoalOutcome {

    private GoalOutcome() {
    }

    /**
     * @param claimed  what the tools and the model set; null when nothing was
     * @param scenario the call's scenario, whose goal field is checked
     * @param outcome  what the call's tools recorded
     * @return the disposition the call is filed under
     */
    static Disposition settle(Disposition claimed, ScenarioDefinition scenario, Map<String, Object> outcome) {
        boolean goalClaim = claimed == Disposition.GOAL_REACHED || claimed == Disposition.GOAL_NOT_REACHED;
        String goalField = scenario == null ? null : scenario.findGoalField();
        if (goalField == null) {
            // Nothing to check the claim against: the call finished, and that is all it can say.
            return goalClaim ? Disposition.COMPLETED : claimed;
        }
        boolean open = claimed == null || claimed == Disposition.COMPLETED || claimed == Disposition.HUNG_UP
                || goalClaim;
        if (!open) {
            return claimed;
        }
        if (isRecorded(outcome == null ? null : outcome.get(goalField))) {
            // Reached even when the caller hung up before the goodbye: the order exists.
            return Disposition.GOAL_REACHED;
        }
        // A call that settled nothing is left for the teardown to name from the hang-up.
        return claimed == null || claimed == Disposition.HUNG_UP ? claimed : Disposition.GOAL_NOT_REACHED;
    }

    private static boolean isRecorded(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean flag -> flag;
            case String text -> !text.isBlank() && !"false".equalsIgnoreCase(text.trim());
            case Collection<?> items -> !items.isEmpty();
            case Map<?, ?> fields -> !fields.isEmpty();
            default -> true;
        };
    }
}
