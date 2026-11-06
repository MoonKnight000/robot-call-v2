package uz.murodjon.robotcallv2.crm.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncCounts;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncResume;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncSettings;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncState;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmContactSyncEntity;

@Component
public class CrmContactSyncEntityMapper {

    public CrmContactSyncSettings toCrmContactSyncSettings(CrmContactSyncEntity entity) {
        return new CrmContactSyncSettings(entity.getCompanyId(), entity.isEnabled(), entity.getFieldMappings(),
                entity.getPushMappings(), entity.getUpdatedAt());
    }

    public CrmContactSyncState toCrmContactSyncState(CrmContactSyncEntity entity) {
        CrmContactSyncResume resume = entity.getResumePageToken() != null
                ? new CrmContactSyncResume(entity.getResumeSince(), entity.getResumePageToken()) : null;
        return new CrmContactSyncState(entity.getProvider(), entity.getCursor(), resume, entity.getNextRunAt(),
                entity.getLastRunAt(), entity.getLastStatus(), entity.getLastError(),
                new CrmContactSyncCounts(entity.getLastCreated(), entity.getLastUpdated(), entity.getLastSkipped()));
    }

    public void copyState(CrmContactSyncState state, CrmContactSyncEntity entity) {
        entity.setProvider(state.provider());
        entity.setCursor(state.cursor());
        entity.setResumeSince(state.resume() != null ? state.resume().since() : null);
        entity.setResumePageToken(state.resume() != null ? state.resume().pageToken() : null);
        entity.setNextRunAt(state.nextRunAt());
        entity.setLastRunAt(state.lastRunAt());
        entity.setLastStatus(state.lastStatus());
        entity.setLastError(state.lastError());
        entity.setLastCreated(state.lastCounts().created());
        entity.setLastUpdated(state.lastCounts().updated());
        entity.setLastSkipped(state.lastCounts().skipped());
    }
}
