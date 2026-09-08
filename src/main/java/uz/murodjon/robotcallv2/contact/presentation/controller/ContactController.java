package uz.murodjon.robotcallv2.contact.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.contact.application.dto.ContactDetail;
import uz.murodjon.robotcallv2.contact.application.dto.ContactImportResult;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactRequest;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Contact management API (§10.8).
 */
@RequestMapping("/api")
public interface ContactController {

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
    ResponseEntity<ResponseData<ContactImportResult>> importCsv(@CurrentCompanyId long companyId, @RequestBody String csv);

    /** "DNC ga qo'shish" (§10.8 drawer) — adds the contact's phone to the opt-out list. */
    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping("/contacts/{id:\\d+}/dnc")
    ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(@CurrentCompanyId long companyId, @PathVariable long id);
}
