package uz.murodjon.robotcallv2.report.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.ReportScheduleEntity;

@Component
public class ReportScheduleMapper {

    public ReportSchedule toDomain(ReportScheduleEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReportSchedule(
                entity.getId(),
                entity.getCompanyId() != null ? entity.getCompanyId() : 0L,
                entity.getEmail(),
                entity.getPeriodicity(),
                entity.getFormat(),
                entity.getCampaignId(),
                entity.isEnabled(),
                entity.getLastSentAt(),
                entity.getCreatedAt()
        );
    }
}
