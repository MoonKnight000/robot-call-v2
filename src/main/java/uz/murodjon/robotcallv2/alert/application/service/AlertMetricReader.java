package uz.murodjon.robotcallv2.alert.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertMetricRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Reads a rule's metric over its window ending {@code now}; shared by the scheduler and the preview. */
@Component
public class AlertMetricReader {

    private static final Set<Disposition> CONVERSIONS = conversions();
    private static final Set<Disposition> FAILURES = EnumSet.of(Disposition.FAILED, Disposition.CARRIER_REJECTED);

    private final AlertMetricRepository repository;

    public AlertMetricReader(AlertMetricRepository repository) {
        this.repository = repository;
    }

    public AlertMetricValue findValue(AlertRule rule, Instant now) {
        Instant since = now.minus(Duration.ofMinutes(rule.windowMinutes()));
        long companyId = rule.companyId();
        Long aiAgentId = rule.aiAgentId();
        return switch (rule.metric()) {
            case SUCCESS_RATE -> repository.findDispositionRate(companyId, aiAgentId, since, CONVERSIONS);
            case TRANSFER_RATE -> repository.findDispositionRate(companyId, aiAgentId, since,
                    EnumSet.of(Disposition.TRANSFERRED));
            case FAILED_CALL_RATE -> repository.findDispositionRate(companyId, aiAgentId, since, FAILURES);
            case NEGATIVE_SENTIMENT_RATE -> repository.findNegativeSentimentRate(companyId, aiAgentId, since);
            case AVG_TURN_LATENCY_MS -> repository.findAverageTurnLatency(companyId, aiAgentId, since);
            case TOOL_ERROR_COUNT -> repository.countToolErrors(companyId, aiAgentId, since);
            case CALL_COUNT -> repository.countEndedCalls(companyId, aiAgentId, since);
        };
    }

    /** The success definition the reports use ({@link Disposition#isConversion}). */
    private static Set<Disposition> conversions() {
        Set<Disposition> conversions = EnumSet.noneOf(Disposition.class);
        for (Disposition disposition : Disposition.values()) {
            if (disposition.isConversion()) {
                conversions.add(disposition);
            }
        }
        return conversions;
    }
}
