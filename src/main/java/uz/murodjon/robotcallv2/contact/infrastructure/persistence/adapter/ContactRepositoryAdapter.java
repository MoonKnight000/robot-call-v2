package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.mapper.ContactEntityMapper;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ContactRepositoryAdapter implements ContactRepository {

    private final ContactJpaRepository contactJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ContactEntityMapper mapper;

    public ContactRepositoryAdapter(ContactJpaRepository contactJpaRepository,
                                  CompanyJpaRepository companyJpaRepository,
                                  ContactEntityMapper mapper) {
        this.contactJpaRepository = contactJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, Contact contact) {
        CompanyEntity comp = companyJpaRepository.getReferenceById(companyId);

        ContactEntity entity = new ContactEntity();
        entity.setCompany(comp);
        entity.setName(contact.name());
        entity.setPhone(contact.phone());
        entity.setAddress(contact.address());
        entity.setTagsFromString(contact.tags());
        entity.setNotes(contact.notes());
        entity.setCustomFields(contact.customFields());
        entity.setCreatedAt(Instant.now());
        return contactJpaRepository.save(entity).getId();
    }

    @Override
    public Contact find(long companyId, long id) {
        return contactJpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toContact).orElse(null);
    }

    @Override
    public boolean existsByPhone(long companyId, String phone) {
        return contactJpaRepository.existsByCompanyIdAndPhone(companyId, phone);
    }

    @Override
    public Contact findByPhone(long companyId, String phone) {
        return contactJpaRepository.findByCompanyIdAndPhone(companyId, phone).map(mapper::toContact).orElse(null);
    }

    @Override
    public Map<String, String> findNamesByPhones(long companyId, Collection<String> phones) {
        if (phones.isEmpty()) {
            return Map.of();
        }
        return contactJpaRepository.findNamesByPhones(companyId, phones).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (String) row[1]));
    }

    @Override
    public void update(long companyId, long id, Contact contact) {
        contactJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setName(contact.name());
            entity.setAddress(contact.address());
            entity.setTagsFromString(contact.tags());
            entity.setNotes(contact.notes());
            entity.setCustomFields(contact.customFields());
            contactJpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        contactJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(contactJpaRepository::delete);
    }

    @Override
    public void removeCustomFieldKey(long companyId, String key) {
        contactJpaRepository.removeCustomFieldKey(companyId, key);
    }

    @Override
    public PageableData<Contact> findPage(long companyId, ContactFilter filter) {
        // The page carries its total, so no second count query of our own.
        Page<ContactEntity> page = contactJpaRepository.findAll(filterSpecification(companyId, filter),
                filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toContact).toList(),
                filter.pageOrDefault(), filter.sizeOrDefault(), page.getTotalElements());
    }

    /** The company's contacts narrowed by every filter the request set. */
    private static Specification<ContactEntity> filterSpecification(long companyId, ContactFilter filter) {
        String search = filter.search();
        String pattern = search == null || search.isBlank() ? null : LikePatterns.buildContainsPattern(search.toLowerCase());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (pattern != null) {
                // The JSON text holds the keys too; a key that matches the search is harmless noise.
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, LikePatterns.ESCAPE),
                        cb.like(root.get("phone"), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("customFields").as(String.class)), pattern, LikePatterns.ESCAPE)));
            }
            if (!filter.tags().isEmpty()) {
                Subquery<Long> tagged = query.subquery(Long.class);
                Root<ContactEntity> tagRoot = tagged.from(ContactEntity.class);
                Join<ContactEntity, String> tag = tagRoot.join("tags");
                tagged.select(tagRoot.get("id")).where(tag.in(filter.tags()));
                predicates.add(root.get("id").in(tagged));
            }
            filter.customFields().forEach((key, value) -> predicates.add(cb.equal(
                    cb.lower(cb.function("jsonb_extract_path_text", String.class, root.get("customFields"), cb.literal(key))),
                    value.toLowerCase())));
            if (filter.lastCallFrom() != null || filter.lastCallTo() != null) {
                Subquery<Instant> lastCall = query.subquery(Instant.class);
                Root<CallAttemptEntity> call = lastCall.from(CallAttemptEntity.class);
                lastCall.select(cb.greatest(call.<Instant>get("startedAt"))).where(
                        cb.equal(call.get("company").get("id"), companyId),
                        cb.equal(call.get("phone"), root.get("phone")));
                if (filter.lastCallFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(lastCall, filter.lastCallFrom()));
                }
                if (filter.lastCallTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(lastCall, filter.lastCallTo()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
