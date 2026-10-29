package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Operating mode of an AI Agent in Retell-style architecture:
 * - {@link #SINGLE_PROMPT}: The agent is guided by one comprehensive system prompt,
 *   a dynamic or static welcome message, tools, and variables. Best for natural,
 *   adaptive conversations, customer support, and general consultations.
 * - {@link #CONVERSATIONAL_FLOW}: The agent strictly navigates through an interconnected
 *   graph of stages (nodes) with deterministic or intent-driven transitions (edges).
 *   Best for compliance-driven, rigid multi-step procedures (e.g. debt collection, intake).
 */
public enum AiAgentMode {
    SINGLE_PROMPT,
    CONVERSATIONAL_FLOW;

    public ScenarioMode toScenarioMode() {
        return this == CONVERSATIONAL_FLOW ? ScenarioMode.STRUCTURED_STEPS : ScenarioMode.PROMPT;
    }

    public static AiAgentMode fromScenarioMode(ScenarioMode scenarioMode) {
        if (scenarioMode == ScenarioMode.STRUCTURED_STEPS) {
            return CONVERSATIONAL_FLOW;
        }
        return SINGLE_PROMPT;
    }
}
