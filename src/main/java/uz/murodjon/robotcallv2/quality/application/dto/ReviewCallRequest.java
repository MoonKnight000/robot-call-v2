package uz.murodjon.robotcallv2.quality.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;

/**
 * {@code PUT /api/call-reviews/{callId}}.
 *
 * @param status {@code AGENT_CORRECT} or {@code AGENT_WRONG}
 * @param reason what the agent got wrong; required for {@code AGENT_WRONG}
 */
public record ReviewCallRequest(
        @NotNull CallReviewStatus status,
        @Size(max = 1000) String reason
) {
}
