package uz.murodjon.robotcallv2.alert.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertMetricRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.repository.AlertMetricJpaRepository;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.repository.AlertMetricProjection;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Set;

@Component
public class AlertMetricRepositoryAdapter implements AlertMetricRepository {

    private final AlertMetricJpaRepository jpaRepository;

    public AlertMetricRepositoryAdapter(AlertMetricJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AlertMetricValue findDispositionRate(long companyId, Long aiAgentId, Instant since,
                                                Set<Disposition> dispositions) {
        return toRate(jpaRepository.findDispositionCounts(companyId, aiAgentId, since,
                dispositions.stream().map(Enum::name).toList()));
    }

    @Override
    public AlertMetricValue findNegativeSentimentRate(long companyId, Long aiAgentId, Instant since) {
        return toRate(jpaRepository.findNegativeSentimentCounts(companyId, aiAgentId, since));
    }

    @Override
    public AlertMetricValue findAverageTurnLatency(long companyId, Long aiAgentId, Instant since) {
        AlertMetricProjection row = jpaRepository.findTurnLatency(companyId, aiAgentId, since);
        return new AlertMetricValue(row.getTotal() == 0 ? null : row.getAverage(), row.getTotal());
    }

    @Override
    public AlertMetricValue countToolErrors(long companyId, Long aiAgentId, Instant since) {
        AlertMetricProjection row = jpaRepository.findToolErrorCounts(companyId, aiAgentId, since);
        return new AlertMetricValue((double) row.getHits(), row.getTotal());
    }

    @Override
    public AlertMetricValue countEndedCalls(long companyId, Long aiAgentId, Instant since) {
        long total = jpaRepository.countEndedCalls(companyId, aiAgentId, since);
        return new AlertMetricValue((double) total, total);
    }

    /** {@code hits} as a percentage of {@code total}; no value when there is no total. */
    private static AlertMetricValue toRate(AlertMetricProjection row) {
        return new AlertMetricValue(row.getTotal() == 0 ? null : row.getHits() * 100.0 / row.getTotal(), row.getTotal());
    }
}
