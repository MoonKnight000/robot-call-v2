package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactPrivacyRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.SubjectCall;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactPrivacyJpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Every table that holds a caller's number or words (NEXT-PLAN S.7). One adapter rather than
 * a method in each feature because an erasure is one act: it reaches every table in one
 * transaction, or it has not happened.
 */
@Repository
public class ContactPrivacyRepositoryAdapter implements ContactPrivacyRepository {

    /** What an erased call's summary reads, so a report row still says why it is empty. */
    private static final String ERASED_TEXT = "[o'chirildi]";

    private final ContactPrivacyJpaRepository jpaRepository;

    public ContactPrivacyRepositoryAdapter(ContactPrivacyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<SubjectCall> findCalls(long companyId, Collection<String> phones) {
        return jpaRepository.findCalls(companyId, phones).stream()
                .map(row -> new SubjectCall(row.getId(), row.getStartedAt(), row.getDurationSec(),
                        row.getDisposition() != null ? row.getDisposition().name() : null, row.getSummary()))
                .toList();
    }

    @Override
    public List<Long> findFileIds(long companyId, Collection<String> phones) {
        return jpaRepository.findFileIds(companyId, phones);
    }

    /**
     * The statements that find calls by number run first: once {@code call_attempt.phone}
     * holds the replacement, the number no longer leads to its transcripts.
     */
    @Override
    @Transactional
    public int erase(long companyId, Collection<String> phones, String replacement) {
        jpaRepository.deleteTranscripts(companyId, phones);
        jpaRepository.clearEventPayloads(companyId, phones);
        jpaRepository.eraseResults(companyId, phones, ERASED_TEXT);
        int calls = jpaRepository.anonymizeAttempts(companyId, phones, replacement);
        jpaRepository.anonymizeTargets(companyId, phones, replacement);
        jpaRepository.anonymizeVoicemails(companyId, phones, replacement);
        jpaRepository.anonymizeMissedCalls(companyId, phones, replacement);
        jpaRepository.anonymizeConversions(companyId, phones, replacement);
        jpaRepository.deleteWebhookDeliveries(companyId, phones);
        jpaRepository.deleteClientMemories(companyId, phones);
        jpaRepository.deleteContacts(companyId, phones);
        return calls;
    }
}
