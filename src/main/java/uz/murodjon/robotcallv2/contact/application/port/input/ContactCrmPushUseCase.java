package uz.murodjon.robotcallv2.contact.application.port.input;

import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;

import java.time.Instant;
import java.util.List;

/**
 * The contacts whose values still owe their CRM record a write (Retell's "Sync to CRM"). A
 * contact owes one when its fields change by hand or after a call — never when the CRM import
 * itself changed it.
 */
public interface ContactCrmPushUseCase {

    /** Due writes, the longest-waiting first, across companies. */
    List<PendingCrmPush> findDueCrmPushes(Instant now, int limit);

    /**
     * Records the write's outcome: {@code error} {@code null} — done (or nothing to write),
     * else it is tried again later, and given up after {@code CrmPushRetryPolicy.MAX_ATTEMPTS}.
     * A change requested after {@code push.requestedAt()} stays owed either way.
     */
    void finishCrmPush(PendingCrmPush push, String error);
}
