package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeCondition;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeMatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Judges METRICS outcomes: the first one, in list order, whose conditions hold over the call's
 * variables. PROMPT outcomes are left to the LLM classifier.
 */
public final class OutcomeMatcher {

    private OutcomeMatcher() {
    }

    public static Optional<SequenceOutcome> findMetricsMatch(List<SequenceOutcome> outcomes,
                                                              Map<String, Object> variables) {
        return outcomes.stream()
                .filter(outcome -> outcome.evaluation() == OutcomeEvaluation.METRICS && !outcome.conditions().isEmpty())
                .filter(outcome -> matches(outcome, variables))
                .findFirst();
    }

    static boolean matches(SequenceOutcome outcome, Map<String, Object> variables) {
        return outcome.match() == OutcomeMatch.ANY
                ? outcome.conditions().stream().anyMatch(condition -> holds(condition, variables))
                : outcome.conditions().stream().allMatch(condition -> holds(condition, variables));
    }

    static boolean holds(OutcomeCondition condition, Map<String, Object> variables) {
        if (condition.variable() == null || condition.operator() == null) {
            return false;
        }
        Object raw = variables.get(condition.variable());
        String actual = raw == null ? null : String.valueOf(raw).trim();
        boolean set = actual != null && !actual.isEmpty();
        String expected = condition.value() == null ? "" : condition.value().trim();
        return switch (condition.operator()) {
            case IS_SET -> set;
            case IS_NOT_SET -> !set;
            case EQUALS -> set && actual.equalsIgnoreCase(expected);
            case NOT_EQUALS -> !set || !actual.equalsIgnoreCase(expected);
            case CONTAINS -> set && lower(actual).contains(lower(expected));
            case NOT_CONTAINS -> !set || !lower(actual).contains(lower(expected));
            case GREATER_THAN -> compare(actual, expected) > 0;
            case GREATER_OR_EQUAL -> compare(actual, expected) >= 0;
            case LESS_THAN -> compare(actual, expected) < 0 && compare(actual, expected) != NOT_NUMBERS;
            case LESS_OR_EQUAL -> compare(actual, expected) <= 0 && compare(actual, expected) != NOT_NUMBERS;
        };
    }

    /** Below every real comparison result, so "greater" checks fail on it; "less" checks test for it. */
    private static final int NOT_NUMBERS = Integer.MIN_VALUE;

    private static int compare(String actual, String expected) {
        try {
            return new BigDecimal(actual).compareTo(new BigDecimal(expected));
        } catch (NumberFormatException | NullPointerException e) {
            return NOT_NUMBERS;
        }
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
