package uz.murodjon.robotcallv2.operator.application.port.output;

import uz.murodjon.robotcallv2.operator.domain.entity.OperatorStatusChange;

import java.time.Instant;
import java.util.List;

public interface OperatorStatusLogRepository {

    /** Close the operator's open row (if any) at {@code at} and open one for the new status. */
    void record(OperatorStatusChange change);

    /** Close the operator's open row without opening another. */
    void closeOpen(long userId, Instant at);

    /** Every operator's current (open) status row. */
    List<OperatorStatusChange> findOpen();
}
