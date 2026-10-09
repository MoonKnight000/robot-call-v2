package uz.murodjon.robotcallv2.scenario.domain.service;

import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** Picks the stage a {@link StageBranch} list sends the call to, from the call's facts. */
public final class StageBranchEvaluator {

    private StageBranchEvaluator() {
    }

    /**
     * @param facts the call's fact by name, null when unset
     * @return the {@code goTo} of the first branch that holds, or null when none does and
     *         the stage is talked through as usual
     */
    public static String findTarget(List<StageBranch> branches, Function<String, Object> facts) {
        if (branches == null) {
            return null;
        }
        for (StageBranch branch : branches) {
            if (holds(branch, facts.apply(branch.fact()))) {
                return branch.goTo();
            }
        }
        return null;
    }

    static boolean holds(StageBranch branch, Object fact) {
        String actual = fact == null ? null : fact.toString().trim();
        boolean present = actual != null && !actual.isEmpty();
        return switch (branch.operator()) {
            case EXISTS -> present;
            case MISSING -> !present;
            case EQUALS -> present && compare(actual, branch.value()) == 0;
            case NOT_EQUALS -> !present || compare(actual, branch.value()) != 0;
            case GREATER -> present && compare(actual, branch.value()) > 0;
            case GREATER_OR_EQUAL -> present && compare(actual, branch.value()) >= 0;
            case LESS -> present && compare(actual, branch.value()) < 0;
            case LESS_OR_EQUAL -> present && compare(actual, branch.value()) <= 0;
        };
    }

    /** As numbers when both sides read as one ("1500000" vs "0"), otherwise as text. */
    private static int compare(String actual, String expected) {
        String other = expected == null ? "" : expected.trim();
        BigDecimal left = toNumber(actual);
        BigDecimal right = toNumber(other);
        if (left != null && right != null) {
            return left.compareTo(right);
        }
        return actual.compareToIgnoreCase(other);
    }

    private static BigDecimal toNumber(String text) {
        try {
            return new BigDecimal(text.replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
