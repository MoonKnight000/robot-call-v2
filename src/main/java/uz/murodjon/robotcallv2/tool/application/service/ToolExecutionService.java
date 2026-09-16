package uz.murodjon.robotcallv2.tool.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolExecutionUseCase;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolExecutionRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;

/**
 * Separate from {@link ToolService} on purpose: that one runs a tool's test through
 * {@code HttpToolExecutor}, and the executor writes its log through this — one class for
 * both would be a dependency cycle.
 */
@Service
public class ToolExecutionService implements ToolExecutionUseCase {

    private final ToolExecutionRepository repository;

    public ToolExecutionService(ToolExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordExecution(ToolExecution execution) {
        repository.record(execution);
    }

    @Override
    public int countFailedExecutions(long companyId, long callAttemptId) {
        return repository.countFailedByCompanyIdAndCallAttemptId(companyId, callAttemptId);
    }
}
