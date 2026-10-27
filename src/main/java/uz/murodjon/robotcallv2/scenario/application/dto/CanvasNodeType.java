package uz.murodjon.robotcallv2.scenario.application.dto;

/**
 * Node types recognized by the Visual Scenario Studio.
 */
public enum CanvasNodeType {
    /** Call entry point / header with call purpose and disclosure text. */
    START,
    /** State machine stage (the primary conversational turn / FSM state). */
    STAGE,
    /** Global question/intent interrupt answered at any stage without leaving it. */
    GLOBAL_NODE,
    /** External webhook or function tool definition. */
    TOOL,
    /** Outcome or business conversion field. */
    OUTCOME
}
