package uz.murodjon.robotcallv2.operator.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

/**
 * Body of PUT /api/operators/me/status.
 *
 * @param status AVAILABLE, BREAK or OFFLINE — the rest are set by the call itself
 * @param reason why, for BREAK ("tushlik")
 */
public record UpdateOperatorStatusRequest(
        @NotNull OperatorStatus status,
        @Size(max = 255) String reason
) {
}
