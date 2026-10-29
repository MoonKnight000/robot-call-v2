package uz.murodjon.robotcallv2.agent.metrics;

import java.time.Instant;

/**
 * When each stage of one turn happened, for laying the turn over the call's recording (the
 * Audio Inspector). The same stamps as {@link TurnLatency}, kept as points on the call's own
 * clock rather than as durations: every offset is milliseconds from the start of the dialog —
 * the clock the agent's transcript lines ({@code ts_offset_ms}) use — and null when that stage
 * was not stamped this turn.
 *
 * @param at              wall-clock time the first audio of the reply went out
 * @param utteranceEndMs  the speech gate closed: the caller was declared finished
 * @param eouWaitMs       silence the gate waited out before closing; the caller actually
 *                        stopped at {@code utteranceEndMs - eouWaitMs}
 * @param sttFinalMs      the recognizer's final text
 * @param llmRequestMs    request sent to the model
 * @param llmFirstTokenMs first token back
 * @param ttsRequestMs    first sentence handed to synthesis
 * @param firstAudioMs    first audio of the reply on the wire
 * @param speculated      the reply was written ahead of the final, so the LLM stamps may be missing
 */
public record TurnTiming(
        Instant at,
        Integer utteranceEndMs,
        Integer eouWaitMs,
        Integer sttFinalMs,
        Integer llmRequestMs,
        Integer llmFirstTokenMs,
        Integer ttsRequestMs,
        int firstAudioMs,
        boolean speculated
) {
}
