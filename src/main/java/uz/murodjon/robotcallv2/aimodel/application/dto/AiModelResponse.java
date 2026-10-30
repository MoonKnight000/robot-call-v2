package uz.murodjon.robotcallv2.aimodel.application.dto;

import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelTier;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

/**
 * A catalog model as the picker receives it ({@code GET /api/ai-models}).
 *
 * @param pricePerMinuteUzs what this model adds to a minute of an ordinary call, in UZS with
 *                          the markup applied: the tokens for a chat model, the engine's minute
 *                          for a realtime one, recognition or synthesis for a speech model
 */
public record AiModelResponse(
        String id,
        AiModelKind kind,
        String provider,
        PipelineMode mode,
        String label,
        AiModelTier tier,
        boolean suggested,
        long pricePerMinuteUzs
) {
}
