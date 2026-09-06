package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.domain.entity.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;

import java.time.Instant;
import java.util.List;

/**
 * Output port SPI for report schedule persistence.
 */
public interface ReportScheduleRepository {

    long create(long companyId, CreateReportScheduleRequest request);

    ReportSchedule find(long companyId, long id);

    List<ReportSchedule> findAll(long companyId, ReportScheduleFilter filter);

    long count(long companyId, ReportScheduleFilter filter);

    void disable(long companyId, long id);

    List<ReportSchedule> findEnabled();

    void markSent(long id, Instant sentAt);
}
