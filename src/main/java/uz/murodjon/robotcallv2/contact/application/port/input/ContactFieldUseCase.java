package uz.murodjon.robotcallv2.contact.application.port.input;

import uz.murodjon.robotcallv2.contact.application.dto.CreateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.ExtractedCallValues;

import java.util.List;

/** The company's contact field definitions (Retell's "Manage contact fields"). */
public interface ContactFieldUseCase {

    List<ContactField> findAllByCompanyId(long companyId);

    ContactField create(long companyId, CreateContactFieldRequest request);

    ContactField update(long companyId, long id, UpdateContactFieldRequest request);

    /** Removes the definition and the field's value from every contact of the company. */
    void delete(long companyId, long id);

    /**
     * Writes what a finished call extracted into the contact with {@code phone}, creating the
     * contact when the company has none for that number. Each company field whose
     * {@code extractionKey} the call filled is written under its update mode; a key the agent
     * routes itself ({@code contactField}) and no company field defines is overwritten.
     * Runs after a call with nobody to report to, so it never throws on bad input: an unusable
     * number, invalid key or a value that does not fit its field's type is dropped and logged.
     */
    void applyCallValues(long companyId, String phone, ExtractedCallValues values);
}
