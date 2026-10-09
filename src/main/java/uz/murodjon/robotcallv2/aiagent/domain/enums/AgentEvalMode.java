package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * How an evaluation plays its test cases.
 *
 * <ul>
 *   <li>{@code TEXT} — the persona's lines reach the agent as clean text; fast and cheap,
 *       tests what the agent decides</li>
 *   <li>{@code VOICE} — the persona is synthesized, put under noise and sent as RTP through
 *       the real call pipeline, so recognition and turn-taking are tested too</li>
 * </ul>
 */
public enum AgentEvalMode {
    TEXT,
    VOICE
}
