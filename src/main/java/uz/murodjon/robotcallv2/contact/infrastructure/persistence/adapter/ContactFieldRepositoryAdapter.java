package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactFieldEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.mapper.ContactFieldEntityMapper;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactFieldJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class ContactFieldRepositoryAdapter implements ContactFieldRepository {

    private final ContactFieldJpaRepository contactFieldJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ContactFieldEntityMapper mapper;

    public ContactFieldRepositoryAdapter(ContactFieldJpaRepository contactFieldJpaRepository,
                                         CompanyJpaRepository companyJpaRepository,
                                         ContactFieldEntityMapper mapper) {
        this.contactFieldJpaRepository = contactFieldJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ContactField> findAllByCompanyId(long companyId) {
        return contactFieldJpaRepository.findAllByCompanyId(companyId).stream().map(mapper::toContactField).toList();
    }

    @Override
    public ContactField find(long companyId, long id) {
        return contactFieldJpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toContactField).orElse(null);
    }

    @Override
    public boolean existsByKey(long companyId, String key) {
        return contactFieldJpaRepository.existsByCompanyIdAndKey(companyId, key);
    }

    @Override
    public long countByCompanyId(long companyId) {
        return contactFieldJpaRepository.countByCompanyId(companyId);
    }

    @Override
    public long create(long companyId, ContactField field) {
        ContactFieldEntity entity = new ContactFieldEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setKey(field.key());
        entity.setType(field.type());
        mapper.copyEditable(field, entity);
        entity.setCreatedAt(Instant.now());
        return contactFieldJpaRepository.save(entity).getId();
    }

    @Override
    public void update(long companyId, long id, ContactField field) {
        contactFieldJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            mapper.copyEditable(field, entity);
            contactFieldJpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        contactFieldJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(contactFieldJpaRepository::delete);
    }
}
