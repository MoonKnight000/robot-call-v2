package uz.murodjon.robotcallv2.crm.application.port.output;

import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncSettings;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncState;

import java.time.Instant;
import java.util.List;

public interface CrmContactSyncRepository {

    /** The company's sync settings, or {@code null} when it never saved any. */
    CrmContactSyncSettings findSettings(long companyId);

    /** The company's run state; {@link CrmContactSyncState#empty()} when it never saved settings. */
    CrmContactSyncState findState(long companyId);

    /** Creates or replaces the settings; the run state is left as it is. */
    void saveSettings(long companyId, CrmContactSyncSettings settings);

    void saveState(long companyId, CrmContactSyncState state);

    /** Companies whose sync is enabled and due at {@code now}, the longest-waiting first. */
    List<Long> findDueCompanyIds(Instant now, int limit);
}
