package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.List;

/**
 * How one test case went in one evaluation run (NEXT-PLAN A.2).
 *
 * @param testCaseId         null once the case has been deleted; {@code caseName} keeps its name
 * @param problems           every expectation it missed, empty when it passed
 * @param transcript         the simulated call, one {@code AGENT:}/{@code CLIENT:} line each
 * @param qaScore            the judge's score, or null when it was not scored
 * @param guardrailViolation the judge found a figure, date or promise the facts do not support
 */
public record AgentEvalResult(
        Long id,
        long runId,
        Long testCaseId,
        String caseName,
        boolean passed,
        String disposition,
        String stage,
        List<String> problems,
        String transcript,
        Integer qaScore,
        boolean guardrailViolation
) {

    public AgentEvalResult {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }
}
