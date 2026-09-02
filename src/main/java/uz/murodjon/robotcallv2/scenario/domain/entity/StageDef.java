package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

public record StageDef(
        String id,
        String purpose,
        List<String> allowedTransitions,
        List<String> allowedTools,
        String emotion
) {
    public StageDef(String id, String purpose, List<String> allowedTransitions, List<String> allowedTools) {
        this(id, purpose, allowedTransitions, allowedTools, null);
    }
}
