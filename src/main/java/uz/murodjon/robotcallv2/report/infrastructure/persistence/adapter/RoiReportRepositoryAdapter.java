package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.application.port.output.RoiReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.repository.RoiReportJpaRepository;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.repository.RoiTotalsProjection;

import java.time.Instant;

@Component
public class RoiReportRepositoryAdapter implements RoiReportRepository {

    private final RoiReportJpaRepository jpaRepository;

    public RoiReportRepositoryAdapter(RoiReportJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RoiTotals findRoiTotals(long companyId, Instant from, Instant to, Long campaignId) {
        RoiTotalsProjection row = jpaRepository.findRoiTotals(companyId, from, to, campaignId);
        return new RoiTotals(row.getTotalCalls(), row.getAnsweredCalls(), row.getTalkSeconds(), row.getSpentUzs(),
                row.getPromises(), row.getPromisedAmount(),
                row.getConversions(), row.getConversionValue());
    }
}
