package uz.murodjon.robotcallv2.copilot.domain.enums;

public enum CopilotMessageRole {
    /** What the person asked. */
    USER,
    /** What the copilot answered. */
    ASSISTANT,
    /** One tool the copilot called while answering, with its arguments and what it got back. */
    TOOL
}
