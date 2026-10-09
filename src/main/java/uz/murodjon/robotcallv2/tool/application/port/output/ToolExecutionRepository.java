package uz.murodjon.robotcallv2.tool.application.port.output;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionFilter;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionStats;

import java.time.Instant;
import java.util.List;

/**
 * The log of the requests company tools sent (UNIVERSAL-OPERATOR-PLAN B.6): read by the
 * call quality row (D.1), the daily insight job (D.3) and the panel's tool log.
 */
public interface ToolExecutionRepository {

    void record(ToolExecution execution);

    int countFailedByCompanyIdAndCallAttemptId(long companyId, long callAttemptId);

    PageableData<ToolExecution> findPageByCompanyId(long companyId, ToolExecutionFilter filter);

    /** One row per tool that sent anything since {@code since}, busiest first. */
    List<ToolExecutionStats> findStatsByCompanyIdSince(long companyId, Instant since);
}
