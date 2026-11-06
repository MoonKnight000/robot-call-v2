package uz.murodjon.robotcallv2.crm.application.port.input;

import uz.murodjon.robotcallv2.crm.application.dto.CrmContactSyncResponse;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmContactSyncRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmFieldOption;

import java.util.List;

/** The CRM → contacts import (Retell's "Set up contact sync" → "Import contacts"). */
public interface CrmContactSyncUseCase {

    CrmContactSyncResponse findContactSync(long companyId);

    /** Saves what is imported; switching the sync on schedules a run at once. */
    CrmContactSyncResponse updateContactSync(long companyId, UpdateCrmContactSyncRequest request);

    /** The connected CRM's contact fields, read live, for the mapping screen. */
    List<CrmFieldOption> findCrmContactFields(long companyId);

    /**
     * Schedules a run within the next minute; {@code full} forgets the cursor, so every CRM
     * contact is read again (Retell's "Run full sync").
     */
    CrmContactSyncResponse requestContactSyncRun(long companyId, boolean full);
}
