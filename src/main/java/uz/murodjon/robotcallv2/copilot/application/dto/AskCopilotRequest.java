package uz.murodjon.robotcallv2.copilot.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotPageContext;

/**
 * @param sessionId the conversation to continue; null starts a new one
 * @param message   the question
 * @param context   the panel page the question was asked on; null when none
 */
public record AskCopilotRequest(
        Long sessionId,
        @NotBlank @Size(max = 4000) String message,
        @Valid CopilotPageContext context
) {
}
