package uz.murodjon.robotcallv2.crm.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmSettingsEntity;

@Component
public class CrmSettingsEntityMapper {

    public CrmSettings toCrmSettings(CrmSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CrmSettings(
                entity.getCompanyId(),
                entity.getAiEmployeeId(),
                entity.getMeetingBookedStatusId(),
                entity.getPromiseToPayStatusId(),
                entity.getNotInterestedStatusId(),
                entity.getCallbackStatusId(),
                entity.getNewLeadStatusId(),
                entity.getUpdatedAt());
    }
}
