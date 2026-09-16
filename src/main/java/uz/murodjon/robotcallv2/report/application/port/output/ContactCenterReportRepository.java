package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.OperatorPerformance;
import uz.murodjon.robotcallv2.report.domain.entity.QueuePerformance;

import java.time.Instant;
import java.util.List;

/** How the inbound side served its callers: per queue and per operator. */
public interface ContactCenterReportRepository {

    /** Every queue's SLA, ASA, abandon rate and more for calls that joined it in [from, to). */
    List<QueuePerformance> findQueuePerformance(long companyId, Instant from, Instant to);

    /** Every operator's calls, misses and time in status in [from, to). */
    List<OperatorPerformance> findOperatorPerformance(long companyId, Instant from, Instant to);
}
