package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.contact.application.dto.ContactFilter;
import uz.murodjon.robotcallv2.contact.application.mapper.ContactMapper;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
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

    private final ContactJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ContactMapper mapper;

    public ContactRepositoryAdapter(ContactJpaRepository jpaRepository,
                                  CompanyJpaRepository companyJpaRepository,
                                  ContactMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, String name, String phone, String address, String tags, String notes) {
        CompanyEntity comp = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        ContactEntity entity = new ContactEntity();
        entity.setCompany(comp);
        entity.setName(name);
        entity.setPhone(phone);
        entity.setAddress(address);
        entity.setTags(tags);
        entity.setNotes(notes);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public Contact find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public boolean existsByPhone(long companyId, String phone) {
        return jpaRepository.existsByCompanyIdAndPhone(companyId, phone);
    }

    @Override
    public Map<String, String> namesByPhones(long companyId, Collection<String> phones) {
        if (phones.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findNamesByPhones(companyId, phones).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (String) row[1]));
    }

    @Override
    public void update(long companyId, long id, String name, String address, String tags, String notes) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setName(name);
            entity.setAddress(address);
            entity.setTags(tags);
            entity.setNotes(notes);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public List<Contact> findAll(long companyId, ContactFilter filter) {
        return jpaRepository.search(companyId, likePattern(filter.search()), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(long companyId, ContactFilter filter) {
        return jpaRepository.countSearch(companyId, likePattern(filter.search()));
    }

    private static String likePattern(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }
}
