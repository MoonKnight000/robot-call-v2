package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentEvalRunDetail;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;

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
     * @param mode    TEXT plays the cases as text, VOICE as audio through the real call pipeline
     * @param repeat  how many times each case is played, 1–10; a case passes only when every attempt does
     */
    AgentEvalRun evaluate(long companyId, long agentId, Integer version, AgentEvalMode mode, int repeat);

    List<AgentEvalRun> findRuns(long companyId, long agentId);

    AgentEvalRunDetail findRun(long companyId, long agentId, long runId);
}
