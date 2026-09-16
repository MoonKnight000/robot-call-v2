package uz.murodjon.robotcallv2.tool.infrastructure.persistence.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolExecutionRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.mapper.ToolExecutionEntityMapper;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository.ToolExecutionJpaRepository;

@Component
public class ToolExecutionRepositoryAdapter implements ToolExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionRepositoryAdapter.class);

    private final ToolExecutionJpaRepository jpaRepository;
    private final ToolExecutionEntityMapper mapper;

    public ToolExecutionRepositoryAdapter(ToolExecutionJpaRepository jpaRepository, ToolExecutionEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void record(ToolExecution execution) {
        try {
            jpaRepository.save(mapper.toToolExecutionEntity(execution));
        } catch (Exception e) {
            // This runs on a live call. Losing the log line is a smaller problem than
            // losing the turn it was describing.
            log.warn("Tool execution log failed for {}: {}", execution.toolName(), e.getMessage());
        }
    }

    @Override
    public int countFailedByCompanyIdAndCallAttemptId(long companyId, long callAttemptId) {
        return (int) jpaRepository.countByCompanyIdAndCallAttemptIdAndStatusNot(companyId, callAttemptId,
                ToolExecutionStatus.OK);
    }
}
