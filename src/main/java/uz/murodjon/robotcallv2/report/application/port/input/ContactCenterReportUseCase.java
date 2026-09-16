package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.report.application.dto.OperatorReportRow;
import uz.murodjon.robotcallv2.report.application.dto.QueueReportRow;

import java.util.List;

public interface ContactCenterReportUseCase {

    /** @param from ISO-8601 instant or date; null for seven days before {@code to} */
    List<QueueReportRow> findQueueReport(long companyId, String from, String to);

    List<OperatorReportRow> findOperatorReport(long companyId, String from, String to);

    /** One call's timeline: flow nodes, keys, queueing, operators rung, transfers, survey. */
    List<CallEvent> findCallEvents(long companyId, long callId);
}
