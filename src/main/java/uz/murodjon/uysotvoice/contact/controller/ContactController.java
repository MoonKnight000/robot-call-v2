package uz.murodjon.uysotvoice.contact.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.contact.dto.ContactDetail;
import uz.murodjon.uysotvoice.contact.dto.ContactFilter;
import uz.murodjon.uysotvoice.contact.dto.ContactImportResult;
import uz.murodjon.uysotvoice.contact.dto.Contact;
import uz.murodjon.uysotvoice.contact.dto.CreateContactRequest;
import uz.murodjon.uysotvoice.contact.dto.UpdateContactRequest;
import uz.murodjon.uysotvoice.donotcall.dto.ContactDncResponse;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/** Contact management API (§10.8) — not in ROADMAP, designed fresh for the panel. */
@RequestMapping("/api")
public interface ContactController {

    @PostMapping("/contacts")
    ResponseEntity<ResponseData<Contact>> create(@Valid @RequestBody CreateContactRequest r);

    @PostMapping("/contacts/list")
    ResponseEntity<ResponseData<PageableData<Contact>>> list(@Valid @RequestBody ContactFilter filter);

    /** Profile + call-history timeline (§10.8 drawer). */
    @GetMapping("/contacts/{id}")
    ResponseEntity<ResponseData<ContactDetail>> get(@PathVariable long id);

    @PutMapping("/contacts/{id}")
    ResponseEntity<ResponseData<Contact>> update(@PathVariable long id, @Valid @RequestBody UpdateContactRequest r);

    /**
     * Bulk-load contacts from a CSV export (§10.8). Send the file body as {@code text/csv}:
     *
     * <pre>
     * name,phone,address,tags,notes
     * Aziz Karimov,998901234567,Toshkent,vip,Doimiy mijoz
     * </pre>
     *
     * <p>Columns are matched by header name; bad rows are reported by line number and
     * everything else is loaded.
     */
    @PostMapping(value = "/contacts/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<ContactImportResult>> importCsv(@RequestBody String csv);

    /** "DNC ga qo'shish" (§10.8 drawer) — adds the contact's phone to the opt-out list. */
    @PostMapping("/contacts/{id}/dnc")
    ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(@PathVariable long id);
}
