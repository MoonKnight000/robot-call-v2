package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentEvalRunDetail;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;

import java.util.List;

/** An agent's regression suite and its runs (NEXT-PLAN A.2). */
public interface AgentEvaluationUseCase {

    List<AgentTestCase> findTestCases(long companyId, long agentId);

    AgentTestCase createTestCase(long companyId, long agentId, SaveAgentTestCaseRequest request);

    AgentTestCase updateTestCase(long companyId, long agentId, long caseId, SaveAgentTestCaseRequest request);

    void deleteTestCase(long companyId, long agentId, long caseId);

    /**
     * Starts playing every test case against the agent in the background and returns the
     * run as it starts.
     *
     * @param version a published version to evaluate, or null for the draft
     */
    AgentEvalRun evaluate(long companyId, long agentId, Integer version);

    List<AgentEvalRun> findRuns(long companyId, long agentId);

    AgentEvalRunDetail findRun(long companyId, long agentId, long runId);
}
