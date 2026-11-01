package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * Settings of a PRESS_DIGIT function.
 *
 * @param pauseDetectionDelayMs how long the agent waits after pressing before it goes on (the menu
 *                              needs a moment to answer); null means no extra wait
 */
public record AgentFunctionPressDigit(Integer pauseDetectionDelayMs) {
}
