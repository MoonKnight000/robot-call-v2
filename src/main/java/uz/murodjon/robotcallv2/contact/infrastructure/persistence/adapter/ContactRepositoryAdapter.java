package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
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
import java.util.Collection;
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
            contactJpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        contactJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(contactJpaRepository::delete);
    }

    @Override
    public PageableData<Contact> findPage(long companyId, ContactFilter filter) {
        // The page carries its total, so no second count query of our own.
        Page<ContactEntity> page = contactJpaRepository.findAll(searchSpecification(companyId, filter.search()),
                filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toContact).toList(),
                filter.pageOrDefault(), filter.sizeOrDefault(), page.getTotalElements());
    }

    /** The company's contacts whose name or number contains the search text; a blank search does not narrow. */
    private static Specification<ContactEntity> searchSpecification(long companyId, String search) {
        String pattern = search == null ? null : LikePatterns.buildContainsPattern(search.toLowerCase());
        return (root, query, cb) -> {
            Predicate ownCompany = cb.equal(root.get("company").get("id"), companyId);
            if (pattern == null) {
                return ownCompany;
            }
            return cb.and(ownCompany, cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, LikePatterns.ESCAPE),
                    cb.like(root.get("phone"), pattern, LikePatterns.ESCAPE)));
        };
    }
}
