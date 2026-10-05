package uz.murodjon.robotcallv2.contact.presentation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Contact management API (docs/api/contacts.md, §10.8).
 */
@RequestMapping("/api")
public interface ContactController {

    int MAX_CSV_CHARS = 5_000_000;

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping("/contacts")
    ResponseEntity<ResponseData<Contact>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateContactRequest request);

    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @PostMapping({"/contacts/list", "/contacts/filter"})
    ResponseEntity<ResponseData<PageableData<Contact>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ContactFilter filter);

    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @GetMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<ContactDetail>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PutMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<Contact>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateContactRequest request);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping(value = "/contacts/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<ContactImportResult>> importCsv(
            @CurrentCompanyId long companyId,
            @RequestBody @Size(max = MAX_CSV_CHARS) String csv);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping("/contacts/{id:\\d+}/dnc")
    ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @GetMapping("/contacts/data")
    ResponseEntity<ResponseData<ContactDataExport>> exportData(
            @CurrentCompanyId long companyId,
            @RequestParam String phone);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/contacts/data")
    ResponseEntity<ResponseData<ContactErasureResponse>> eraseData(
            @CurrentCompanyId long companyId,
            @RequestParam String phone,
            @RequestParam(defaultValue = "false") boolean confirm);
}
