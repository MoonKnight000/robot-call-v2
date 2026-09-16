package uz.murodjon.robotcallv2.contact.presentation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.contact.application.dto.ContactDataExport;
import uz.murodjon.robotcallv2.contact.application.dto.ContactDetail;
import uz.murodjon.robotcallv2.contact.application.dto.ContactErasureResponse;
import uz.murodjon.robotcallv2.contact.application.dto.ContactImportResult;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactRequest;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.contact.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Contact management API (§10.8).
 */
@RequestMapping("/api")
public interface ContactController {

    /**
     * A CSV body is a raw String, so the 5 MB multipart limit never applies to it; this
     * caps it at about the same size (a 400, not an import of whatever was sent).
     */
    int MAX_CSV_CHARS = 5_000_000;

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping("/contacts")
    ResponseEntity<ResponseData<Contact>> create(@CurrentCompanyId long companyId,
                                                 @Valid @RequestBody CreateContactRequest request);

    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @PostMapping({"/contacts/list", "/contacts/filter"})
    ResponseEntity<ResponseData<PageableData<Contact>>> list(@CurrentCompanyId long companyId,
            @Valid @RequestBody ContactFilter filter);

    /** Profile + call-history timeline (§10.8 drawer). */
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @GetMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<ContactDetail>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PutMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<Contact>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                 @Valid @RequestBody UpdateContactRequest request);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId, @PathVariable long id);

    /**
     * Bulk-load contacts from a CSV export (§10.8).
     */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping(value = "/contacts/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<ContactImportResult>> importCsv(@CurrentCompanyId long companyId, @RequestBody @Size(max = MAX_CSV_CHARS) String csv);

    /** "DNC ga qo'shish" (§10.8 drawer) — adds the contact's phone to the opt-out list. */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping("/contacts/{id:\\d+}/dnc")
    ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(@CurrentCompanyId long companyId, @PathVariable long id);

    /**
     * Everything the company holds about one number — contact, memory, every call with its
     * summary and full transcript — for a data-subject request (NEXT-PLAN S.7). Both
     * spellings of the number ({@code +998…}, {@code 998…}) are searched.
     */
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @GetMapping("/contacts/data")
    ResponseEntity<ResponseData<ContactDataExport>> exportData(@CurrentCompanyId long companyId,
                                                                @RequestParam String phone);

    /**
     * Irreversibly deletes or anonymizes everything held about one number; the do-not-call
     * list is kept. {@code confirm=true} is required, otherwise 400
     * {@code SUBJECT_ERASURE_NOT_CONFIRMED}.
     */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/contacts/data")
    ResponseEntity<ResponseData<ContactErasureResponse>> eraseData(@CurrentCompanyId long companyId,
                                                                   @RequestParam String phone,
                                                                   @RequestParam(defaultValue = "false") boolean confirm);
}
