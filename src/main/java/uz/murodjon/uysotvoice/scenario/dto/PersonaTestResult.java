package uz.murodjon.uysotvoice.scenario.dto;

import java.util.List;
import java.util.Map;

public record PersonaTestResult(
        String personaName,
        String personaDescription,
        boolean passed,
        String finalDisposition,
        List<Map<String, String>> dialogTranscript,
        Map<String, Object> finalOutcome,
        String failureReason
) {
}
