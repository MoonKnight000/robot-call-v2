package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.dto.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;

import java.time.Instant;
import java.util.List;

/**
 * Output port SPI for report schedule persistence.
 */
public interface ReportScheduleRepository {

    long create(CreateReportScheduleRequest r);

    ReportSchedule find(long id);

    List<ReportSchedule> findAll(ReportScheduleFilter filter);

    long count(ReportScheduleFilter filter);

    void disable(long id);

    List<ReportSchedule> findEnabled();

    void markSent(long id, Instant sentAt);
}
