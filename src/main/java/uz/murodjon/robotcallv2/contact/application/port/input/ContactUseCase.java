package uz.murodjon.robotcallv2.contact.application.port.input;

import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.Map;

/** Inbound UseCase port for Contact management (§10.8). */
public interface ContactUseCase {

    Contact create(CreateContactRequest r);

    Contact update(long id, UpdateContactRequest r);

    void delete(long id);

    PageableData<Contact> list(ContactFilter filter);

    ContactDetail detail(long id);

    Contact requireContact(long id);

    Map<String, String> namesByPhones(Collection<String> phones);

    ContactImportResult importCsv(String csv);

    ContactDncResponse addToDoNotCall(long id);
}
