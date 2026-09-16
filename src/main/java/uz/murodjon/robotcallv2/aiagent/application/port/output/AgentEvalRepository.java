package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;

import java.time.Instant;
import java.util.List;

public interface AgentEvalRepository {

    long createRun(AgentEvalRun run);

    void finishRun(long runId, AgentEvalRunStatus status, int passed, Integer avgQaScore, Instant finishedAt,
                   String error);

    AgentEvalRun findRun(long companyId, long agentId, long runId);

    /** The agent's latest runs, newest first. */
    List<AgentEvalRun> findRuns(long companyId, long agentId, int limit);

    /** Whether a run of this agent started after {@code since} is still running. */
    boolean hasRunningSince(long companyId, long agentId, Instant since);

    void addResult(AgentEvalResult result);

    List<AgentEvalResult> findResults(long runId);
}
