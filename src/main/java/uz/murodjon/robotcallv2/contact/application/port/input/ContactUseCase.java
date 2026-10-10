package uz.murodjon.robotcallv2.contact.application.port.input;

import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.Map;

/** Inbound UseCase port for Contact management (§10.8). */
public interface ContactUseCase {

    Contact create(long companyId, CreateContactRequest request);

    Contact update(long companyId, long id, UpdateContactRequest request);

    void delete(long companyId, long id);

    PageableData<Contact> list(long companyId, ContactFilter filter);

    /** The contact with its call-history timeline — 404 when the company has no such contact. */
    ContactDetail requireContactDetail(long companyId, long id);

    Contact requireContact(long companyId, long id);

    Map<String, String> findNamesByPhones(long companyId, Collection<String> phones);

    ContactImportResult importCsv(long companyId, String csv);

    ContactDncResponse addToDoNotCall(long companyId, long id);

    /** Everything held about {@code phone}: contact, memory, calls with transcripts (NEXT-PLAN S.7). */
    ContactDataExport exportSubjectData(long companyId, String phone);

    /** Deletes or anonymizes everything held about {@code phone}; {@code confirm} must be true. */
    ContactErasureResponse eraseSubjectData(long companyId, String phone, boolean confirm);

    /** Pre-call carrier, format, and Do-Not-Call scrubbing for a batch of telephone numbers. */
    ContactScrubResponse scrubPhones(long companyId, ContactScrubRequest request);
}
