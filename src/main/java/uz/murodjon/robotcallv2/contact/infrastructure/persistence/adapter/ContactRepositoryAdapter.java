package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.contact.application.mapper.ContactMapper;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ContactRepositoryAdapter implements ContactRepository {

    private final ContactJpaRepository contactJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ContactMapper mapper;

    public ContactRepositoryAdapter(ContactJpaRepository contactJpaRepository,
                                  CompanyJpaRepository companyJpaRepository,
                                  ContactMapper mapper) {
        this.contactJpaRepository = contactJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, Contact contact) {
        CompanyEntity comp = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        ContactEntity entity = new ContactEntity();
        entity.setCompany(comp);
        entity.setName(contact.name());
        entity.setPhone(contact.phone());
        entity.setAddress(contact.address());
        entity.setTags(contact.tags());
        entity.setNotes(contact.notes());
        entity.setCreatedAt(Instant.now());
        return contactJpaRepository.save(entity).getId();
    }

    @Override
    public Contact find(long companyId, long id) {
        return contactJpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public boolean existsByPhone(long companyId, String phone) {
        return contactJpaRepository.existsByCompanyIdAndPhone(companyId, phone);
    }

    @Override
    public Map<String, String> namesByPhones(long companyId, Collection<String> phones) {
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
            entity.setTags(contact.tags());
            entity.setNotes(contact.notes());
            contactJpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        contactJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(contactJpaRepository::delete);
    }

    @Override
    public List<Contact> findAll(long companyId, ContactFilter filter) {
        return contactJpaRepository.search(companyId, likePattern(filter.search()), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(long companyId, ContactFilter filter) {
        return contactJpaRepository.countSearch(companyId, likePattern(filter.search()));
    }

    private static String likePattern(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }
}
