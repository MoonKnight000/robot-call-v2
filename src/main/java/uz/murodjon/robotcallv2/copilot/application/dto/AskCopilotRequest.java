package uz.murodjon.robotcallv2.copilot.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param sessionId the conversation to continue; null starts a new one
 * @param message   the question
 */
public record AskCopilotRequest(
        Long sessionId,
        @NotBlank @Size(max = 4000) String message
) {
}
