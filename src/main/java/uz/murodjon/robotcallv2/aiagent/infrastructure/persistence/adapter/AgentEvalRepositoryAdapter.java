package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentEvalRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentEvalResultEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentEvalRunEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper.AgentEvaluationEntityMapper;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AgentEvalResultJpaRepository;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AgentEvalRunJpaRepository;

import java.time.Instant;
import java.util.List;

@Repository
public class AgentEvalRepositoryAdapter implements AgentEvalRepository {

    private final AgentEvalRunJpaRepository runJpaRepository;
    private final AgentEvalResultJpaRepository resultJpaRepository;
    private final AgentEvaluationEntityMapper mapper;

    public AgentEvalRepositoryAdapter(AgentEvalRunJpaRepository runJpaRepository,
                                      AgentEvalResultJpaRepository resultJpaRepository,
                                      AgentEvaluationEntityMapper mapper) {
        this.runJpaRepository = runJpaRepository;
        this.resultJpaRepository = resultJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long createRun(AgentEvalRun run) {
        AgentEvalRunEntity entity = new AgentEvalRunEntity();
        entity.setCompanyId(run.companyId());
        entity.setAgentId(run.agentId());
        entity.setAgentVersion(run.agentVersion());
        entity.setStatus(run.status());
        entity.setTotal(run.total());
        entity.setPassed(run.passed());
        entity.setStartedAt(run.startedAt());
        return runJpaRepository.save(entity).getId();
    }

    @Override
    public void finishRun(long runId, AgentEvalRunStatus status, int passed, Integer avgQaScore, Instant finishedAt,
                          String error) {
        runJpaRepository.finish(runId, status, passed, avgQaScore, finishedAt, error);
    }

    @Override
    public AgentEvalRun findRun(long companyId, long agentId, long runId) {
        return runJpaRepository.findByIdAndCompanyIdAndAgentId(runId, companyId, agentId)
                .map(mapper::toAgentEvalRun)
                .orElse(null);
    }

    @Override
    public List<AgentEvalRun> findRuns(long companyId, long agentId, int limit) {
        return runJpaRepository.findByCompanyIdAndAgentIdOrderByStartedAtDesc(companyId, agentId,
                        PageRequest.of(0, limit)).stream()
                .map(mapper::toAgentEvalRun)
                .toList();
    }

    @Override
    public boolean hasRunningSince(long companyId, long agentId, Instant since) {
        return runJpaRepository.existsByCompanyIdAndAgentIdAndStatusAndStartedAtAfter(companyId, agentId,
                AgentEvalRunStatus.RUNNING, since);
    }

    @Override
    public void addResult(AgentEvalResult result) {
        AgentEvalResultEntity entity = new AgentEvalResultEntity();
        entity.setRunId(result.runId());
        entity.setTestCaseId(result.testCaseId());
        entity.setCaseName(result.caseName());
        entity.setPassed(result.passed());
        entity.setDisposition(result.disposition());
        entity.setStage(result.stage());
        entity.setProblems(mapper.write(result.problems()));
        entity.setTranscript(result.transcript() == null ? "" : result.transcript());
        entity.setQaScore(result.qaScore());
        entity.setGuardrailViolation(result.guardrailViolation());
        entity.setCreatedAt(Instant.now());
        resultJpaRepository.save(entity);
    }

    @Override
    public List<AgentEvalResult> findResults(long runId) {
        return resultJpaRepository.findByRunIdOrderById(runId).stream()
                .map(mapper::toAgentEvalResult)
                .toList();
    }
}
