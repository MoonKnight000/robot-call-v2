package uz.murodjon.robotcallv2.aimodel.domain.entity;

import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelTier;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

/**
 * A row of ai_model — one model an operator may pick: the company chat model
 * ({@code ai_model_config.model}) or one of an agent's three ({@code ai_agent.llm_model},
 * {@code stt_model}, {@code tts_model}), depending on {@link AiModelKind}.
 *
 * <p>The catalog exists so that a model that cannot answer a call is refused when it is
 * typed, not when the phone is already ringing: the form is filled from here and the
 * save-time check reads the same rows.
 *
 * @param id       model id sent to the provider verbatim, e.g. {@code gemini-3.8-flash}
 * @param kind     which of the agent's model fields this row fills
 * @param provider who serves it — for {@code LLM} a chat vendor ({@code google-genai}, {@code openai}, {@code anthropic})
 *                 (CASCADE) or a {@code RealtimeProvider} name (REALTIME), for {@code STT}
 *                 and {@code TTS} the speech provider id ({@code gemini}, {@code yandex})
 * @param mode     the pipeline this model belongs to; the two sets never overlap
 * @param label    human-readable name shown in the settings and agent forms
 * @param tier     what a chat model is for, which is how the picker groups it; null for
 *                 rows that are not tiered (speech models, realtime engines)
 * @param suggested whether the picker puts this model forward first
 */
public record AiModel(
        String id,
        AiModelKind kind,
        String provider,
        PipelineMode mode,
        String label,
        AiModelTier tier,
        boolean suggested
) {

    /**
     * Determines whether a given model identifier belongs to a realtime / speech-to-speech engine
     * (e.g. Gemini Live, OpenAI Realtime, Qwen Omni) that only communicates via WebSockets (bidiGenerateContent),
     * and therefore CANNOT be called via standard text turn-based ChatModel HTTP endpoints.
     */
    public static boolean isRealtimeModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String lower = modelId.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.contains("-live")
                || lower.contains("realtime")
                || lower.contains("omni")
                || lower.equals("moshi")
                || lower.contains("native-audio");
    }
}
