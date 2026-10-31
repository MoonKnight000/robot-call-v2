package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * An integration tool to execute to fetch the audience for a batch call or campaign.
 */
public record BatchCallToolSourceDto(
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
