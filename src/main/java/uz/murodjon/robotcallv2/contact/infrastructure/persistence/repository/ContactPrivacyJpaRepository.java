package uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;

import java.util.Collection;
import java.util.List;

/**
 * Every table that holds a caller's number or words (NEXT-PLAN S.7). The erase statements are
 * native: most of those tables have no entity in this feature, and each is one bulk statement.
 */
public interface ContactPrivacyJpaRepository extends Repository<ContactEntity, Long> {

    /** The number's calls, by the number they were placed or answered under. */
    String ATTEMPTS = "SELECT id FROM call_attempt WHERE company_id = :companyId AND phone IN (:phones)";

    @Query("SELECT a.id AS id, a.startedAt AS startedAt, a.durationSec AS durationSec, "
            + "a.disposition AS disposition, r.summary AS summary "
            + "FROM CallAttemptEntity a LEFT JOIN CallResultEntity r ON r.call = a "
            + "WHERE a.company.id = :companyId AND a.phone IN :phones ORDER BY a.id")
    List<SubjectCallProjection> findCalls(@Param("companyId") long companyId,
                                          @Param("phones") Collection<String> phones);

    @Query(value = "SELECT recording_file_id FROM call_attempt "
            + "WHERE company_id = :companyId AND phone IN (:phones) AND recording_file_id IS NOT NULL "
            + "UNION SELECT stored_file_id FROM voicemail_message "
            + "WHERE company_id = :companyId AND caller_phone IN (:phones) AND stored_file_id IS NOT NULL",
            nativeQuery = true)
    List<Long> findFileIds(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    @Modifying
    @Query(value = "DELETE FROM call_transcript WHERE call_id IN (" + ATTEMPTS + ")", nativeQuery = true)
    void deleteTranscripts(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    @Modifying
    @Query(value = "UPDATE call_event SET payload = NULL WHERE call_attempt_id IN (" + ATTEMPTS + ")",
            nativeQuery = true)
    void clearEventPayloads(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    @Modifying
    @Query(value = "UPDATE call_result SET summary = :erasedText, follow_up_note = NULL, crm_last_error = NULL, "
            + "outcome = CAST('{}' AS jsonb) WHERE call_id IN (" + ATTEMPTS + ")", nativeQuery = true)
    void eraseResults(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                      @Param("erasedText") String erasedText);

    @Modifying
    @Query(value = "UPDATE call_attempt SET phone = :replacement, recording_file_id = NULL, error_message = NULL "
            + "WHERE company_id = :companyId AND phone IN (:phones)", nativeQuery = true)
    int anonymizeAttempts(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                          @Param("replacement") String replacement);

    /** A queued target would otherwise be dialled at the replacement. */
    @Modifying
    @Query(value = "UPDATE campaign_target SET phone = :replacement, context_data = CAST('{}' AS jsonb), "
            + "next_attempt_at = NULL, "
            + "status = CASE WHEN status IN ('PENDING', 'IN_PROGRESS') THEN 'DONE' ELSE status END "
            + "WHERE company_id = :companyId AND phone IN (:phones)", nativeQuery = true)
    void anonymizeTargets(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                          @Param("replacement") String replacement);

    @Modifying
    @Query(value = "UPDATE voicemail_message SET caller_phone = :replacement, transcript = NULL, stored_file_id = NULL "
            + "WHERE company_id = :companyId AND caller_phone IN (:phones)", nativeQuery = true)
    void anonymizeVoicemails(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                             @Param("replacement") String replacement);

    @Modifying
    @Query(value = "UPDATE missed_call SET phone = :replacement, note = NULL "
            + "WHERE company_id = :companyId AND phone IN (:phones)", nativeQuery = true)
    void anonymizeMissedCalls(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                              @Param("replacement") String replacement);

    @Modifying
    @Query(value = "UPDATE conversion_event SET phone = :replacement, evidence = NULL "
            + "WHERE company_id = :companyId AND phone IN (:phones)", nativeQuery = true)
    void anonymizeConversions(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                              @Param("replacement") String replacement);

    /** A retried post-call webhook carries the number, the summary and the transcript. */
    @Modifying
    @Query(value = "DELETE FROM webhook_delivery WHERE company_id = :companyId AND payload ->> 'phone' IN (:phones)",
            nativeQuery = true)
    void deleteWebhookDeliveries(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    @Modifying
    @Query(value = "DELETE FROM client_memory WHERE company_id = :companyId AND phone IN (:phones)",
            nativeQuery = true)
    void deleteClientMemories(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    @Modifying
    @Query(value = "DELETE FROM contact WHERE company_id = :companyId AND phone IN (:phones)", nativeQuery = true)
    void deleteContacts(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);
}
