package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.shared.csv.CsvRowError;

import java.util.List;
import java.util.Map;

/**
 * Preview result of executing an integration tool for campaign targets.
 */
public record ToolTargetPreviewResponse(
        int totalItems,
        List<String> detectedVariables,
        List<Map<String, Object>> sampleTargets,
        List<CsvRowError> errors
) {
}
