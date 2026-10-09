package uz.murodjon.robotcallv2.alert.application.port.output;

import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Set;

/**
 * The numbers alert rules watch, read off the company's calls since {@code since}. Only calls an
 * AI agent ran count; {@code aiAgentId} null means every agent's.
 */
public interface AlertMetricRepository {

    /** The percentage of ended calls whose disposition is in {@code dispositions}. */
    AlertMetricValue findDispositionRate(long companyId, Long aiAgentId, Instant since, Set<Disposition> dispositions);

    /** The percentage of analysed calls whose caller was read as negative or hostile. */
    AlertMetricValue findNegativeSentimentRate(long companyId, Long aiAgentId, Instant since);

    /** The average of the calls' average turn latency, in ms. */
    AlertMetricValue findAverageTurnLatency(long companyId, Long aiAgentId, Instant since);

    /** Tool requests that did not come back 2xx; the sample is all tool requests. */
    AlertMetricValue countToolErrors(long companyId, Long aiAgentId, Instant since);

    AlertMetricValue countEndedCalls(long companyId, Long aiAgentId, Instant since);
}
