package uz.murodjon.robotcallv2.contact.application.port.output;

import uz.murodjon.robotcallv2.contact.domain.entity.SubjectCall;

import java.util.Collection;
import java.util.List;

/**
 * Everything a company holds about one phone number, across the tables that hold it
 * (NEXT-PLAN S.7). {@code phones} is every spelling the number may be stored under
 * ({@code +998…} and {@code 998…}).
 */
public interface ContactPrivacyRepository {

    /** The number's calls, oldest first. */
    List<SubjectCall> findCalls(long companyId, Collection<String> phones);

    /** Stored files that carry the number's voice: call recordings and voicemail messages. */
    List<Long> findFileIds(long companyId, Collection<String> phones);

    /**
     * Deletes or anonymizes every row about the number in one transaction and returns how
     * many calls were anonymized. Rows kept for reporting get {@code replacement} in place
     * of the number; the do-not-call list is not touched.
     */
    int erase(long companyId, Collection<String> phones, String replacement);
}
