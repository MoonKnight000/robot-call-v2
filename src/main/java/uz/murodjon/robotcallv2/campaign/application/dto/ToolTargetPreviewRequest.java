package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request to execute a tool (or target source) and preview the extracted audience and dynamic variables.
 */
public record ToolTargetPreviewRequest(
        @NotBlank String toolName,
        Map<String, Object> toolArguments,
        String itemsPath,
        String phoneField,
        String clientIdField,
        String languageField
) {
    public String phoneFieldOrDefault() {
        return phoneField != null && !phoneField.isBlank() ? phoneField.trim() : "phone";
    }
}
