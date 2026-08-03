package uz.murodjon.uysotvoice.aimodel.dto;

/** Every field optional — null clears the override back to the process default. */
public record UpdateAiModelConfigRequest(
        String model,
        Double temperature,
        Integer maxOutputTokens,
        Integer maxCallSeconds,
        Long maxTokensPerCall
) {
}
