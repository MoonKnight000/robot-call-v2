package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Scenario mode of an AI Agent:
 * - PROMPT: The agent is driven primarily by natural language instructions, rules, and goal.
 * - STRUCTURED_STEPS: The agent follows a defined state machine (FSM) of stages with explicit transitions.
 */
public enum ScenarioMode {
    PROMPT,
    STRUCTURED_STEPS
}
