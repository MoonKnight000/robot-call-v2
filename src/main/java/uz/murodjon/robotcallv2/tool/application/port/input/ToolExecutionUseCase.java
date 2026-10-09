package uz.murodjon.robotcallv2.tool.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionFilter;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionStats;

import java.util.List;

/** What the voice pipeline reports about the company tools it ran (UNIVERSAL-OPERATOR-PLAN B.6), and the panel reads back. */
public interface ToolExecutionUseCase {

    /** Best-effort: a log row that could not be written never fails the call it describes. */
    void recordExecution(ToolExecution execution);

    /** The call's tool requests that did not come back 2xx — a quality signal (UNIVERSAL-OPERATOR-PLAN D.1). */
    int countFailedExecutions(long companyId, long callAttemptId);

    PageableData<ToolExecution> filterExecutions(long companyId, ToolExecutionFilter filter);

    /** Per tool, over the last {@code days} days, 1 to 90. */
    List<ToolExecutionStats> findExecutionStats(long companyId, int days);
}
