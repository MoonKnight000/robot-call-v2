package uz.murodjon.robotcallv2.crm.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmSettingsEntity;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmStatusMappingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CrmSettingsEntityMapper {

    public CrmSettings toCrmSettings(CrmSettingsEntity entity, List<CrmStatusMappingEntity> mappings) {
        if (entity == null && (mappings == null || mappings.isEmpty())) {
            return null;
        }
        long companyId = entity != null ? entity.getCompanyId() : 0L;
        Integer aiEmployeeId = entity != null ? entity.getAiEmployeeId() : null;
        Integer taskTypeId = entity != null ? entity.getTaskTypeId() : null;
        java.time.Instant updatedAt = entity != null ? entity.getUpdatedAt() : null;

        Map<String, Integer> map = new HashMap<>();
        if (mappings != null) {
            for (CrmStatusMappingEntity mapping : mappings) {
                if (mapping.getDisposition() != null && mapping.getStatusId() != null) {
                    map.put(mapping.getDisposition(), mapping.getStatusId());
                }
            }
        }
        return new CrmSettings(companyId, aiEmployeeId, taskTypeId, map, updatedAt);
    }
}
