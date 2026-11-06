package uz.murodjon.robotcallv2.contact.application.port.output;

import uz.murodjon.robotcallv2.contact.domain.entity.ContactCrmLink;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ContactCrmLinkRepository {

    /** The contact imported from this CRM record, or {@code null}. */
    Long findContactId(long companyId, String provider, String externalId);

    /** The links of these contacts, by contact id; a contact without one is absent. */
    Map<Long, ContactCrmLink> findByContactIds(long companyId, Collection<Long> contactIds);

    /** Points the contact at the CRM record, replacing a link it had to another record. */
    void upsert(long companyId, ContactCrmLink link);

    /** Marks the contact's values as owed to its CRM record, due at once; no-op for a contact without a link. */
    void requestPush(long companyId, long contactId, Instant at);

    /** Owed writes due at {@code now}, the longest-waiting first; {@code customFields} is left empty. */
    List<PendingCrmPush> findDuePushes(Instant now, int limit);

    /** The write landed: clears the request when no newer one came meanwhile, else leaves the newer one due now. */
    void savePushSuccess(PendingCrmPush push, Instant at);

    /**
     * The write failed for the {@code attempts}-th time: due again at {@code nextAttemptAt}, or given up
     * when that is {@code null}. A newer request is left due now either way.
     */
    void savePushFailure(PendingCrmPush push, int attempts, Instant nextAttemptAt, String error);
}
