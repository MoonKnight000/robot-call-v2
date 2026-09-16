package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;

import java.util.List;

public interface AgentTestCaseRepository {

    long create(AgentTestCase testCase);

    void update(long companyId, long id, AgentTestCase testCase);

    AgentTestCase find(long companyId, long agentId, long id);

    List<AgentTestCase> findByAgent(long companyId, long agentId);

    void delete(long companyId, long id);
}
