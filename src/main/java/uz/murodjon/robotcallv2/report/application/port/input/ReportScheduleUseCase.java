package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.dto.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface ReportScheduleUseCase {

    ReportSchedule create(CreateReportScheduleRequest r);

    PageableData<ReportSchedule> list(ReportScheduleFilter filter);

    ReportSchedule requireSchedule(long id);

    ReportSchedule disable(long id);

    void sendDue();
}
