package uz.murodjon.robotcallv2.schedule.application.port.output;

import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessSchedule;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleFilter;

import java.util.List;

public interface BusinessScheduleRepository {

    long create(long companyId, BusinessSchedule schedule);

    void update(long companyId, long id, BusinessSchedule schedule);

    void delete(long companyId, long id);

    BusinessSchedule find(long companyId, long id);

    List<BusinessSchedule> findAll(long companyId, BusinessScheduleFilter filter);

    long count(long companyId);
}
