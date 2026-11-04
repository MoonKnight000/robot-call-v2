package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.report.application.dto.OperatorReportRow;
import uz.murodjon.robotcallv2.report.application.dto.QueueReportRow;
import uz.murodjon.robotcallv2.report.domain.entity.PredictiveDialingStats;

import java.util.List;

public interface ContactCenterReportUseCase {

    /** @param from ISO-8601 instant or date; null for seven days before {@code to} */
    List<QueueReportRow> findQueueReport(long companyId, String from, String to);

    List<OperatorReportRow> findOperatorReport(long companyId, String from, String to);

    /** A PREDICTIVE campaign's dialled, answered, operator, AI-overflow and abandoned calls. */
    PredictiveDialingStats findPredictiveDialingStats(long companyId, long campaignId, String from, String to);

    /** One call's timeline: flow nodes, keys, queueing, operators rung, transfers, survey. */
    List<CallEvent> findCallEvents(long companyId, long callId);

    /** An AI agent call's block-by-block trace: blocks entered, values recorded, functions run. */
    List<CallEvent> findCallTrace(long companyId, long callId);

    /** The same trace for a chat conversation; 404 when the chat is not the company's. */
    List<CallEvent> findChatTrace(long companyId, long chatId);
}
