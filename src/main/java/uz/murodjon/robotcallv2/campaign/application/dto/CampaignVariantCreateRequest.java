package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

/**
 * @param pipelineMode the pipeline this variant's calls run on; null keeps the agent's
 */
public record CampaignVariantCreateRequest(
        @NotBlank @Size(max = 100) String name,
        Long aiAgentId,
        String promptOverride,
        String ttsVoiceId,
        PipelineMode pipelineMode,
        @Min(1) @Max(100) Integer trafficWeight
) {
}
