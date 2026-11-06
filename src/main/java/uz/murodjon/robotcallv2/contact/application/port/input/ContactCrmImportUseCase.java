package uz.murodjon.robotcallv2.contact.application.port.input;

import uz.murodjon.robotcallv2.contact.domain.entity.ImportedCrmContact;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactImportOutcome;

/** Brings CRM records into the contacts (the CRM contact sync's write side). */
public interface ContactCrmImportUseCase {

    /**
     * Files one CRM record as a contact: the contact already imported from it, else the one
     * with its number, else a new one. Mapped values are merged in — a blank one never clears
     * a stored value — and a value that does not fit its contact field's type is dropped.
     * The contact's phone never changes, and nothing is ever deleted.
     */
    ContactImportOutcome importCrmContact(long companyId, ImportedCrmContact contact);
}
