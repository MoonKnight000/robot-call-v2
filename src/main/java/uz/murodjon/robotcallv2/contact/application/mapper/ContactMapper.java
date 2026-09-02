package uz.murodjon.robotcallv2.contact.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;

@Component
public class ContactMapper {

    public Contact entityToDomain(ContactEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Contact(
                entity.getId(),
                entity.getName(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getTags(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }

    public ContactEntity domainToEntity(Contact domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        ContactEntity entity = new ContactEntity();
        entity.setId(domain.id());
        entity.setCompany(company);
        entity.setName(domain.name());
        entity.setPhone(domain.phone());
        entity.setAddress(domain.address());
        entity.setTags(domain.tags());
        entity.setNotes(domain.notes());
        entity.setCreatedAt(domain.createdAt());
        return entity;
    }
}
