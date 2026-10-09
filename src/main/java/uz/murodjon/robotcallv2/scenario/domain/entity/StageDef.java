package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

/**
 * @param model    the LLM this stage runs on instead of the agent's, or null to keep the agent's.
 *                 A greeting or an identity check does not need the model a negotiation does,
 *                 and the difference is paid in cost and time-to-first-token on every turn of it
 * @param branches decisions taken from the call's facts the moment the call enters this
 *                 stage, without the model ({@link StageBranch}); null or empty = none
 */
public record StageDef(
        String id,
        String purpose,
        List<String> allowedTransitions,
        List<String> allowedTools,
        String emotion,
        String model,
        List<StageBranch> branches
) {
    public StageDef(String id, String purpose, List<String> allowedTransitions, List<String> allowedTools) {
        this(id, purpose, allowedTransitions, allowedTools, null, null, null);
    }

    public StageDef(String id, String purpose, List<String> allowedTransitions, List<String> allowedTools,
                    String emotion) {
        this(id, purpose, allowedTransitions, allowedTools, emotion, null, null);
    }

    public StageDef(String id, String purpose, List<String> allowedTransitions, List<String> allowedTools,
                    String emotion, String model) {
        this(id, purpose, allowedTransitions, allowedTools, emotion, model, null);
    }
}
