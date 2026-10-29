package uz.murodjon.robotcallv2.aiagent.application.dto;

import java.util.List;
import java.util.Map;

/**
 * The agent's answer to one test chat turn.
 *
 * @param reply       what the agent said
 * @param stage       the stage it is in now — send it back as the next request's {@code stage}
 * @param outcome     what its tools recorded so far — send it back as the next request's {@code outcome}
 * @param toolCalls   the mocked tool calls this turn made ({@code "TOOL: name {args}"}); empty when
 *                    the tools are real, since a real call is not echoed back
 * @param violations  figures the fact guard caught in the reply — on a call they would not be said
 * @param ended       the agent ended the conversation
 * @param disposition how it filed the conversation once ended; null before
 * @param latencyMs   how long the turn took
 */
public record AgentTestChatResponse(
        String reply,
        String stage,
        Map<String, Object> outcome,
        List<String> toolCalls,
        List<String> violations,
        boolean ended,
        String disposition,
        long latencyMs
) {
}
