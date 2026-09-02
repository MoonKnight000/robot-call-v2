package uz.murodjon.robotcallv2.contact.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * Contact management API (§10.8).
 */
@RequestMapping("/api")
public interface ContactController {

    @PostMapping("/contacts")
    ResponseEntity<ResponseData<Contact>> create(@Valid @RequestBody CreateContactRequest r);

    @PostMapping({"/contacts/list", "/contacts/filter"})
    ResponseEntity<ResponseData<PageableData<Contact>>> list(@Valid @RequestBody ContactFilter filter);

    /** Profile + call-history timeline (§10.8 drawer). */
    @GetMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<ContactDetail>> get(@PathVariable long id);

    @PutMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<Contact>> update(@PathVariable long id, @Valid @RequestBody UpdateContactRequest r);

    @DeleteMapping("/contacts/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@PathVariable long id);

    /**
     * Bulk-load contacts from a CSV export (§10.8).
     */
    @PostMapping(value = "/contacts/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<ContactImportResult>> importCsv(@RequestBody String csv);

    /** "DNC ga qo'shish" (§10.8 drawer) — adds the contact's phone to the opt-out list. */
    @PostMapping("/contacts/{id:\\d+}/dnc")
    ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(@PathVariable long id);
}
