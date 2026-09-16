package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactPrivacyRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.SubjectCall;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Native SQL over every table that holds a caller's number or words (NEXT-PLAN S.7). One
 * adapter rather than a method in each feature because an erasure is one act: it reaches
 * every table in one transaction, or it has not happened.
 */
@Repository
public class ContactPrivacyRepositoryAdapter implements ContactPrivacyRepository {

    /** What an erased call's summary reads, so a report row still says why it is empty. */
    private static final String ERASED_TEXT = "[o'chirildi]";

    /** The number's calls, by the number they were placed or answered under. */
    private static final String ATTEMPTS =
            "SELECT id FROM call_attempt WHERE company_id = :companyId AND phone IN (:phones)";

    private final EntityManager em;

    public ContactPrivacyRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SubjectCall> findCalls(long companyId, Collection<String> phones) {
        List<Object[]> rows = query("SELECT a.id, a.started_at, a.duration_sec, a.disposition, r.summary "
                + "FROM call_attempt a LEFT JOIN call_result r ON r.call_id = a.id "
                + "WHERE a.company_id = :companyId AND a.phone IN (:phones) ORDER BY a.id", companyId, phones)
                .getResultList();
        return rows.stream()
                .map(row -> new SubjectCall(
                        ((Number) row[0]).longValue(),
                        toInstant(row[1]),
                        row[2] != null ? ((Number) row[2]).intValue() : null,
                        row[3] != null ? row[3].toString() : null,
                        row[4] != null ? row[4].toString() : null))
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> findFileIds(long companyId, Collection<String> phones) {
        List<Object> rows = query("SELECT recording_file_id FROM call_attempt "
                + "WHERE company_id = :companyId AND phone IN (:phones) AND recording_file_id IS NOT NULL "
                + "UNION SELECT stored_file_id FROM voicemail_message "
                + "WHERE company_id = :companyId AND caller_phone IN (:phones) AND stored_file_id IS NOT NULL",
                companyId, phones)
                .getResultList();
        return rows.stream().map(id -> ((Number) id).longValue()).toList();
    }

    /**
     * The statements that find calls by number run first: once {@code call_attempt.phone}
     * holds the replacement, the number no longer leads to its transcripts.
     */
    @Override
    @Transactional
    public int erase(long companyId, Collection<String> phones, String replacement) {
        query("DELETE FROM call_transcript WHERE call_id IN (" + ATTEMPTS + ")", companyId, phones)
                .executeUpdate();
        query("UPDATE call_event SET payload = NULL WHERE call_attempt_id IN (" + ATTEMPTS + ")", companyId, phones)
                .executeUpdate();
        query("UPDATE call_result SET summary = :erasedText, follow_up_note = NULL, crm_last_error = NULL, "
                + "outcome = CAST('{}' AS jsonb) WHERE call_id IN (" + ATTEMPTS + ")", companyId, phones)
                .setParameter("erasedText", ERASED_TEXT)
                .executeUpdate();
        int calls = query("UPDATE call_attempt SET phone = :replacement, recording_file_id = NULL, "
                + "error_message = NULL WHERE company_id = :companyId AND phone IN (:phones)", companyId, phones)
                .setParameter("replacement", replacement)
                .executeUpdate();
        // A queued target would otherwise be dialled at the replacement.
        query("UPDATE campaign_target SET phone = :replacement, context_data = CAST('{}' AS jsonb), "
                + "next_attempt_at = NULL, "
                + "status = CASE WHEN status IN ('PENDING', 'IN_PROGRESS') THEN 'DONE' ELSE status END "
                + "WHERE company_id = :companyId AND phone IN (:phones)", companyId, phones)
                .setParameter("replacement", replacement)
                .executeUpdate();
        query("UPDATE voicemail_message SET caller_phone = :replacement, transcript = NULL, stored_file_id = NULL "
                + "WHERE company_id = :companyId AND caller_phone IN (:phones)", companyId, phones)
                .setParameter("replacement", replacement)
                .executeUpdate();
        query("UPDATE missed_call SET phone = :replacement, note = NULL "
                + "WHERE company_id = :companyId AND phone IN (:phones)", companyId, phones)
                .setParameter("replacement", replacement)
                .executeUpdate();
        query("UPDATE conversion_event SET phone = :replacement, evidence = NULL "
                + "WHERE company_id = :companyId AND phone IN (:phones)", companyId, phones)
                .setParameter("replacement", replacement)
                .executeUpdate();
        // A retried post-call webhook carries the number, the summary and the transcript.
        query("DELETE FROM webhook_delivery WHERE company_id = :companyId AND payload ->> 'phone' IN (:phones)",
                companyId, phones)
                .executeUpdate();
        query("DELETE FROM client_memory WHERE company_id = :companyId AND phone IN (:phones)", companyId, phones)
                .executeUpdate();
        query("DELETE FROM contact WHERE company_id = :companyId AND phone IN (:phones)", companyId, phones)
                .executeUpdate();
        return calls;
    }

    private Query query(String sql, long companyId, Collection<String> phones) {
        return em.createNativeQuery(sql)
                .setParameter("companyId", companyId)
                .setParameter("phones", phones);
    }

    /** A native timestamp column comes back as whichever type the driver prefers. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }
}
