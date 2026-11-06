package uz.murodjon.robotcallv2.contact.application.port.output;

import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;

import java.util.List;

public interface ContactFieldRepository {

    /** The company's fields in the order they were created. */
    List<ContactField> findAllByCompanyId(long companyId);

    ContactField find(long companyId, long id);

    /** Whether the company already has a field with this key, ignoring case. */
    boolean existsByKey(long companyId, String key);

    long countByCompanyId(long companyId);

    long create(long companyId, ContactField field);

    /** Saves label, options, description, extraction key and update mode; key and type never change. */
    void update(long companyId, long id, ContactField field);

    void delete(long companyId, long id);
}
