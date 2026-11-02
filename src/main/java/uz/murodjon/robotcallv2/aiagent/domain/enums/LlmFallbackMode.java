package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * When an agent's fallback model takes over from its main one (cascade pipeline only).
 * - {@link #SEQUENTIAL}: the fallback starts only after the main model's request has failed.
 * - {@link #CONCURRENT}: the fallback runs alongside and steps in when the main model has sent no
 *   first token within the deployment's backup delay (1.5 s by default); the first stream to answer wins.
 */
public enum LlmFallbackMode {
    SEQUENTIAL,
    CONCURRENT
}
