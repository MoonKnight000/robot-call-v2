package uz.murodjon.robotcallv2.contact.application.port.output;

import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.Map;

public interface ContactRepository {

    long create(long companyId, Contact contact);

    Contact find(long companyId, long id);

    boolean existsByPhone(long companyId, String phone);

    /** The contact filed under exactly this spelling of the number, or null. */
    Contact findByPhone(long companyId, String phone);

    Map<String, String> findNamesByPhones(long companyId, Collection<String> phones);

    void update(long companyId, long id, Contact contact);

    void delete(long companyId, long id);

    /** One page of the filter's contacts together with the total, from one list query and at most one count. */
    PageableData<Contact> findPage(long companyId, ContactFilter filter);
}
