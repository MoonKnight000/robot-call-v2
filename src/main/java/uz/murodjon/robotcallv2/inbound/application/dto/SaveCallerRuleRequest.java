package uz.murodjon.robotcallv2.inbound.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleAction;

/** Body of POST/PUT /api/inbound-caller-rules. */
public record SaveCallerRuleRequest(
        @NotBlank String phonePrefix,
        @NotNull CallerRuleAction action,
        @Size(max = 1000) String message,
        @Size(max = 255) String note
) {
}
