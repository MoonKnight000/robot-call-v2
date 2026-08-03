package uz.murodjon.uysotvoice.contact.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.contact.dto.ContactFilter;
import uz.murodjon.uysotvoice.contact.dto.Contact;
import uz.murodjon.uysotvoice.contact.entity.ContactEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** JPA-backed DAO for {@code contact} (§10.8). */
@Repository
public class ContactRepository {

    private final ContactJpaRepository jpa;
    private final CurrentCompany company;

    public ContactRepository(ContactJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public long create(String name, String phone, String address, String tags, String notes) {
        ContactEntity entity = new ContactEntity();
        entity.setCompanyId(company.id());
        entity.setName(name);
        entity.setPhone(phone);
        entity.setAddress(address);
        entity.setTags(tags);
        entity.setNotes(notes);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company — another company's id reads as missing, not found. */
    public Contact find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(ContactRepository::toRow).orElse(null);
    }

    public boolean existsByPhone(String phone) {
        return jpa.existsByCompanyIdAndPhone(company.id(), phone);
    }

    /** Cheap phone→name lookup for other features to enrich rows (e.g. {@code DoNotCallRow}). */
    public Map<String, String> namesByPhones(Collection<String> phones) {
        if (phones.isEmpty()) {
            return Map.of();
        }
        return jpa.findNamesByPhones(company.id(), phones).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (String) row[1]));
    }

    /** No-op if {@code id} does not belong to the current company. */
    public void update(long id, String name, String address, String tags, String notes) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setName(name);
            entity.setAddress(address);
            entity.setTags(tags);
            entity.setNotes(notes);
            jpa.save(entity);
        });
    }

    public List<Contact> findAll(ContactFilter filter) {
        return jpa.search(company.id(), likePattern(filter.search()), filter.pageable()).stream()
                .map(ContactRepository::toRow)
                .toList();
    }

    public long count(ContactFilter filter) {
        return jpa.countSearch(company.id(), likePattern(filter.search()));
    }

    private static String likePattern(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }

    private static Contact toRow(ContactEntity e) {
        return new Contact(e.getId(), e.getName(), e.getPhone(), e.getAddress(), e.getTags(),
                e.getNotes(), e.getCreatedAt());
    }
}
