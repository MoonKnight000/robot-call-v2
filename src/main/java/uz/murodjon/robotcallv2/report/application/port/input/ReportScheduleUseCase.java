package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.domain.entity.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface ReportScheduleUseCase {

    ReportSchedule create(long companyId, CreateReportScheduleRequest request);

    PageableData<ReportSchedule> list(long companyId, ReportScheduleFilter filter);

    ReportSchedule requireSchedule(long companyId, long id);

    ReportSchedule disable(long companyId, long id);

    void sendDue();
}
