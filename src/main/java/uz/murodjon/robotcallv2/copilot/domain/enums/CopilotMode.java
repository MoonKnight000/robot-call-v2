package uz.murodjon.robotcallv2.copilot.domain.enums;

import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

/**
 * How hard the copilot thinks before it answers, picked per question in the panel. More thinking
 * is a slower and costlier answer; the model stays the same.
 */
public enum CopilotMode {

    /** Quick answers and simple lookups. */
    LOW(ReasoningEffort.LOW),
    MEDIUM(ReasoningEffort.MEDIUM),
    /** Multi-step analysis and changes across several screens. */
    PRO(ReasoningEffort.HIGH);

    private final ReasoningEffort reasoningEffort;

    CopilotMode(ReasoningEffort reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public ReasoningEffort reasoningEffort() {
        return reasoningEffort;
    }
}
