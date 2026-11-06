package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactCrmLink;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactCrmLinkEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.mapper.ContactCrmLinkEntityMapper;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactCrmLinkJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ContactCrmLinkRepositoryAdapter implements ContactCrmLinkRepository {

    private final ContactCrmLinkJpaRepository contactCrmLinkJpaRepository;
    private final ContactCrmLinkEntityMapper mapper;

    public ContactCrmLinkRepositoryAdapter(ContactCrmLinkJpaRepository contactCrmLinkJpaRepository,
                                           ContactCrmLinkEntityMapper mapper) {
        this.contactCrmLinkJpaRepository = contactCrmLinkJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Long findContactId(long companyId, String provider, String externalId) {
        return contactCrmLinkJpaRepository.findByExternalId(companyId, provider, externalId)
                .map(ContactCrmLinkEntity::getContactId).orElse(null);
    }

    @Override
    public Map<Long, ContactCrmLink> findByContactIds(long companyId, Collection<Long> contactIds) {
        if (contactIds.isEmpty()) {
            return Map.of();
        }
        return contactCrmLinkJpaRepository.findByContactIds(companyId, contactIds).stream()
                .map(mapper::toContactCrmLink)
                .collect(Collectors.toMap(ContactCrmLink::contactId, link -> link));
    }

    @Override
    @Transactional
    public void upsert(long companyId, ContactCrmLink link) {
        contactCrmLinkJpaRepository.deleteOtherLinks(companyId, link.provider(), link.externalId(), link.contactId());
        ContactCrmLinkEntity entity = contactCrmLinkJpaRepository.findById(link.contactId())
                .orElseGet(ContactCrmLinkEntity::new);
        entity.setContactId(link.contactId());
        entity.setCompanyId(companyId);
        entity.setProvider(link.provider());
        entity.setExternalId(link.externalId());
        entity.setSyncedAt(link.syncedAt());
        contactCrmLinkJpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void requestPush(long companyId, long contactId, Instant at) {
        contactCrmLinkJpaRepository.requestPush(companyId, contactId, at);
    }

    @Override
    public List<PendingCrmPush> findDuePushes(Instant now, int limit) {
        return contactCrmLinkJpaRepository.findDuePushes(now, PageRequest.of(0, limit)).stream()
                .map(mapper::toPendingCrmPush)
                .toList();
    }

    @Override
    @Transactional
    public void savePushSuccess(PendingCrmPush push, Instant at) {
        contactCrmLinkJpaRepository.clearPush(push.contactId(), push.requestedAt(), at);
        contactCrmLinkJpaRepository.dueNewerPush(push.contactId(), push.requestedAt(), at);
    }

    @Override
    @Transactional
    public void savePushFailure(PendingCrmPush push, int attempts, Instant nextAttemptAt, String error) {
        if (nextAttemptAt != null) {
            contactCrmLinkJpaRepository.retryPush(push.contactId(), push.requestedAt(), attempts, nextAttemptAt, error);
        } else {
            contactCrmLinkJpaRepository.abandonPush(push.contactId(), push.requestedAt(), attempts, error);
        }
        contactCrmLinkJpaRepository.dueNewerPush(push.contactId(), push.requestedAt(), Instant.now());
    }
}
