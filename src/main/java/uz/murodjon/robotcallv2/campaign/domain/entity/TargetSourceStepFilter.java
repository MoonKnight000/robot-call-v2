package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.StepFilterOperator;

/**
 * One condition a listed row has to satisfy to be worth carrying through the rest of the
 * chain.
 *
 * @param path     dot path inside the row ({@code "delay"}, {@code "client.status"}).
 *                 A numeric segment indexes an array: {@code "phones.0"}
 * @param operator how {@code value} is compared with what {@code path} resolves to
 * @param value    the comparison operand, as text. Ignored by {@code PRESENT}/{@code ABSENT}
 */
public record TargetSourceStepFilter(
        String path,
        StepFilterOperator operator,
        String value
) {
}
