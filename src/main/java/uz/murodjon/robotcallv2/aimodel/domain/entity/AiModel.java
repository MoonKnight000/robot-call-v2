package uz.murodjon.robotcallv2.aimodel.domain.entity;

import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

/**
 * A row of ai_model — one model an operator may pick, for the whole company
 * ({@code ai_model_config.model}) or for a single agent ({@code ai_agent.llm_model}).
 *
 * <p>The catalog exists so that a model that cannot answer a call is refused when it is
 * typed, not when the phone is already ringing: the form is filled from here and the
 * save-time check reads the same rows.
 *
 * @param id       model id sent to the provider verbatim, e.g. {@code gemini-3.8-flash}
 * @param provider who serves it — a {@code spring.ai.model.chat} value for CASCADE, a
 *                 {@code RealtimeProvider} name for REALTIME
 * @param mode     the pipeline this model belongs to; the two sets never overlap
 * @param label    human-readable name shown in the settings and agent forms
 */
public record AiModel(
        String id,
        String provider,
        PipelineMode mode,
        String label
) {
}
