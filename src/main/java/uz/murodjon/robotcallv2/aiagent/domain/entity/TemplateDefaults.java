package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.List;

/**
 * Default preset values provided by an agent template.
 */
public record TemplateDefaults(
        String firstMessage,
        String systemPrompt,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation
) {
    public TemplateDefaults {
        if (dataNeeded == null) {
            dataNeeded = List.of();
        }
        if (dataEvaluation == null) {
            dataEvaluation = List.of();
        }
    }
}
