package uz.murodjon.robotcallv2.contact.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The company's contact field definitions (docs/api/contacts.md#contact-fields): typed keys of a
 * contact's {@code customFields}, and which post-call extraction field fills each one.
 */
@RequestMapping("/api/contacts/fields")
public interface ContactFieldController {

    @PreAuthorize("hasAuthority('CONTACT_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<ContactField>>> list(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<ContactField>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateContactFieldRequest request);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<ContactField>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateContactFieldRequest request);

    @PreAuthorize("hasAuthority('CONTACT_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
