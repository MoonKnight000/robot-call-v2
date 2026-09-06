package uz.murodjon.robotcallv2.contact.application.port.output;

import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ContactRepository {

    long create(long companyId, Contact contact);

    Contact find(long companyId, long id);

    boolean existsByPhone(long companyId, String phone);

    Map<String, String> namesByPhones(long companyId, Collection<String> phones);

    void update(long companyId, long id, Contact contact);

    void delete(long companyId, long id);

    List<Contact> findAll(long companyId, ContactFilter filter);

    long count(long companyId, ContactFilter filter);
}
