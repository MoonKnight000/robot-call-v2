package uz.murodjon.robotcallv2.copilot.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotPageContext;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMode;

/**
 * @param sessionId the conversation to continue; null starts a new one
 * @param message   the question
 * @param context   the panel page the question was asked on; null when none
 * @param applyChanges true runs the copilot's changes at once; null or false keeps each as a proposal
 *                     the user applies or rejects in the panel
 * @param mode      how hard the copilot thinks: LOW, MEDIUM or PRO; null is LOW
 */
public record AskCopilotRequest(
        Long sessionId,
        @NotBlank @Size(max = 4000) String message,
        @Valid CopilotPageContext context,
        Boolean applyChanges,
        CopilotMode mode
) {

    public boolean applyChangesOrDefault() {
        return Boolean.TRUE.equals(applyChanges);
    }

    public CopilotMode modeOrDefault() {
        return mode != null ? mode : CopilotMode.LOW;
    }
}
