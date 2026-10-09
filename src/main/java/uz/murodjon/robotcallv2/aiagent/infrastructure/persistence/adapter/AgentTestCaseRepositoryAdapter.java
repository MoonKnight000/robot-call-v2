package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentTestCaseRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentTestCaseEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper.AgentEvaluationEntityMapper;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AgentTestCaseJpaRepository;

import java.time.Instant;
import java.util.List;

@Repository
public class AgentTestCaseRepositoryAdapter implements AgentTestCaseRepository {

    private final AgentTestCaseJpaRepository jpaRepository;
    private final AgentEvaluationEntityMapper mapper;

    public AgentTestCaseRepositoryAdapter(AgentTestCaseJpaRepository jpaRepository, AgentEvaluationEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(AgentTestCase testCase) {
        AgentTestCaseEntity entity = new AgentTestCaseEntity();
        entity.setCompanyId(testCase.companyId());
        entity.setAgentId(testCase.agentId());
        entity.setCreatedAt(Instant.now());
        apply(entity, testCase);
        return jpaRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public void update(long companyId, long id, AgentTestCase testCase) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            apply(entity, testCase);
            jpaRepository.save(entity);
        });
    }

    @Override
    public AgentTestCase find(long companyId, long agentId, long id) {
        return jpaRepository.findByIdAndCompanyIdAndAgentId(id, companyId, agentId)
                .map(mapper::toAgentTestCase)
                .orElse(null);
    }

    @Override
    public List<AgentTestCase> findByAgent(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentIdOrderById(companyId, agentId).stream()
                .map(mapper::toAgentTestCase)
                .toList();
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    private void apply(AgentTestCaseEntity entity, AgentTestCase testCase) {
        entity.setName(testCase.name());
        entity.setPersonaPrompt(testCase.personaPrompt());
        entity.setFacts(mapper.write(testCase.facts()));
        entity.setLanguage(testCase.language());
        entity.setExpectDisposition(testCase.expectDisposition());
        entity.setExpectStage(testCase.expectStage());
        entity.setMustSay(mapper.write(testCase.mustSay()));
        entity.setMustNotSay(mapper.write(testCase.mustNotSay()));
        entity.setMaxTurns(testCase.maxTurns());
        entity.setToolMocks(mapper.write(testCase.toolMocks()));
        entity.setExpectTools(mapper.write(testCase.expectTools()));
    }
}
