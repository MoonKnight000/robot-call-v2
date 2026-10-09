package uz.murodjon.robotcallv2.tool.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolExecutionUseCase;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolExecutionRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionFilter;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionStats;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Separate from {@link ToolService} on purpose: that one runs a tool's test through
 * {@code HttpToolExecutor}, and the executor writes its log through this — one class for
 * both would be a dependency cycle.
 */
@Service
public class ToolExecutionService implements ToolExecutionUseCase {

    /** The widest stats window: the aggregate scans the company's rows in it. */
    public static final int MAX_STATS_DAYS = 90;

    private final ToolExecutionRepository repository;
    private final Clock clock;

    public ToolExecutionService(ToolExecutionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void recordExecution(ToolExecution execution) {
        repository.record(execution);
    }

    @Override
    public int countFailedExecutions(long companyId, long callAttemptId) {
        return repository.countFailedByCompanyIdAndCallAttemptId(companyId, callAttemptId);
    }

    @Override
    public PageableData<ToolExecution> filterExecutions(long companyId, ToolExecutionFilter filter) {
        return repository.findPageByCompanyId(companyId, filter);
    }

    @Override
    public List<ToolExecutionStats> findExecutionStats(long companyId, int days) {
        if (days < 1 || days > MAX_STATS_DAYS) {
            throw new ValidationException(ErrorCode.TOOL_STATS_DAYS_INVALID, MAX_STATS_DAYS, days);
        }
        return repository.findStatsByCompanyIdSince(companyId, clock.instant().minus(Duration.ofDays(days)));
    }
}
