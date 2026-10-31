package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * A single direct target item in a batch call request with dynamic variables.
 */
public record BatchCallTargetItemDto(
        @NotBlank String phone,
        Long clientId,
        String language,
        Map<String, Object> variables
) {
}
