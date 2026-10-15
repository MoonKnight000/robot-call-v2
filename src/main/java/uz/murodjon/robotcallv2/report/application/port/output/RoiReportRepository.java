package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;

import java.time.Instant;

public interface RoiReportRepository {

    /** Totals over the company's calls started in [from, to); only one campaign's when {@code campaignId} is set. */
    RoiTotals findRoiTotals(long companyId, Instant from, Instant to, Long campaignId);
}
