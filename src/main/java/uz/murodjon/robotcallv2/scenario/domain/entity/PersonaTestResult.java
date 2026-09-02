package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;
import java.util.Map;

public record PersonaTestResult(
        String personaName,
        String personaPrompt,
        boolean passed,
        String disposition,
        List<Map<String, String>> transcript,
        Map<String, Object> extractedFacts,
        String errorMessage
) {
}
