package uz.murodjon.robotcallv2.contact.application.port.input;

import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.Map;

/** Inbound UseCase port for Contact management (§10.8). */
public interface ContactUseCase {

    Contact create(long companyId, CreateContactRequest request);

    Contact update(long companyId, long id, UpdateContactRequest request);

    void delete(long companyId, long id);

    PageableData<Contact> list(long companyId, ContactFilter filter);

    ContactDetail detail(long companyId, long id);

    Contact requireContact(long companyId, long id);

    Map<String, String> namesByPhones(long companyId, Collection<String> phones);

    ContactImportResult importCsv(long companyId, String csv);

    ContactDncResponse addToDoNotCall(long companyId, long id);
}
