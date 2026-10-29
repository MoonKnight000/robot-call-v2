package uz.murodjon.robotcallv2.agent.dialog;

import java.util.List;
import java.util.Map;

/**
 * One turn of a studio test chat, as {@link StudioTestChat} played it.
 *
 * @param reply       what the agent said
 * @param stage       the stage it is in now
 * @param outcome     what its tools recorded so far
 * @param toolCalls   the mocked tool calls of this turn ({@code "TOOL: name {args}"})
 * @param violations  figures the fact guard caught in the reply
 * @param ended       the agent ended the conversation
 * @param disposition how it filed the conversation once ended; null before
 */
public record StudioTestTurn(
        String reply,
        String stage,
        Map<String, Object> outcome,
        List<String> toolCalls,
        List<String> violations,
        boolean ended,
        String disposition
) {
}
