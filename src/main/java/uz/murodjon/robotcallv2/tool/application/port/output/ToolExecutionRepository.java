package uz.murodjon.robotcallv2.tool.application.port.output;

import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;

/**
 * The log of the requests company tools sent (UNIVERSAL-OPERATOR-PLAN B.6).
 *
 * <p>Not something a screen pages through yet: it is the source the call quality row (D.1) and
 * the daily insight job (D.3) read.
 */
public interface ToolExecutionRepository {

    void record(ToolExecution execution);

    int countFailedByCompanyIdAndCallAttemptId(long companyId, long callAttemptId);
}
