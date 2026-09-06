package uz.murodjon.robotcallv2.campaign.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CampaignVariantUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        Long aiAgentId,
        String promptOverride,
        String ttsVoiceId,
        @Min(1) @Max(100) Integer trafficWeight,
        boolean active
) {
}
