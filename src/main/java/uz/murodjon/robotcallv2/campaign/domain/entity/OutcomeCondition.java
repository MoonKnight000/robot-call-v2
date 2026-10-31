package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeConditionOperator;

/**
 * One comparison of a METRICS outcome: {@code variable operator value}.
 *
 * @param variable a call variable — the target's facts by name, or {@code disposition}
 */
public record OutcomeCondition(String variable, OutcomeConditionOperator operator, String value) {
}
