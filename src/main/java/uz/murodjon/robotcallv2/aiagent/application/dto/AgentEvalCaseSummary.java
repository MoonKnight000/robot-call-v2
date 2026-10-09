package uz.murodjon.robotcallv2.aiagent.application.dto;

/**
 * How one case did over every attempt of a run.
 *
 * @param testCaseId null once the case has been deleted
 * @param passRate   passed attempts among all, in percent
 * @param flaky      some attempts passed and some did not — the agent is not reliable on this case
 */
public record AgentEvalCaseSummary(
        Long testCaseId,
        String caseName,
        int attempts,
        int passed,
        double passRate,
        boolean flaky
) {
}
