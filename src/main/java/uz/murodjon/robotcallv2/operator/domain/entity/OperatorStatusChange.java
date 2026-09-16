package uz.murodjon.robotcallv2.operator.domain.entity;

import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Instant;

/** One row of operator_status_log: a status an operator held from {@code startedAt} until {@code endedAt}. */
public record OperatorStatusChange(
        long userId,
        long companyId,
        OperatorStatus status,
        String reason,
        Instant startedAt,
        Instant endedAt
) {
}
