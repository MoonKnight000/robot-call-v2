package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;

import java.util.List;

public record AgentAnalysisDto(
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation
) {
    public AgentAnalysisDto {
        dataNeeded = dataNeeded == null ? List.of() : List.copyOf(dataNeeded);
        dataEvaluation = dataEvaluation == null ? List.of() : List.copyOf(dataEvaluation);
    }
}
