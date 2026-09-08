package uz.murodjon.robotcallv2.aimodel.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;

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
 * @param provider who serves it — for {@code LLM} a {@code spring.ai.model.chat} value
 *                 (CASCADE) or a {@code RealtimeProvider} name (REALTIME), for {@code STT}
 *                 and {@code TTS} the speech provider id ({@code gemini}, {@code yandex})
 * @param mode     the pipeline this model belongs to; the two sets never overlap
 * @param label    human-readable name shown in the settings and agent forms
 */
public record AiModel(
        String id,
        AiModelKind kind,
        String provider,
        PipelineMode mode,
        String label
) {
}
