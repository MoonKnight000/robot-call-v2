package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallConditionOperator;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * One test a post-call action makes on what the call's analysis extracted before it runs
 * ("only when {@code interested} equals {@code true}").
 *
 * @param field    the extracted value's key in {@code call_result.outcome} — a {@code dataNeeded} field's id
 * @param operator how the value is compared
 * @param value    what it is compared with; ignored by {@code IS_SET} / {@code IS_NOT_SET}
 */
public record PostCallCondition(String field, PostCallConditionOperator operator, String value) {

    /** Whether {@code outcome} passes this test. Text compares ignoring case and surrounding spaces. */
    public boolean matches(Map<String, Object> outcome) {
        Object raw = outcome == null ? null : outcome.get(field);
        String actual = raw == null ? null : String.valueOf(raw).trim();
        boolean set = actual != null && !actual.isEmpty();
        String expected = value == null ? "" : value.trim();
        return switch (operator) {
            case IS_SET -> set;
            case IS_NOT_SET -> !set;
            case EQUALS -> set && actual.equalsIgnoreCase(expected);
            case NOT_EQUALS -> !set || !actual.equalsIgnoreCase(expected);
            case CONTAINS -> set && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case GREATER_THAN -> compare(actual, expected) > 0;
            case LESS_THAN -> compare(actual, expected) < 0;
        };
    }

    /** The numeric comparison of the two, or 0 (neither greater nor less) when either is not a number. */
    private static int compare(String actual, String expected) {
        BigDecimal left = parseNumber(actual);
        BigDecimal right = parseNumber(expected);
        return left == null || right == null ? 0 : left.compareTo(right);
    }

    public static BigDecimal parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
