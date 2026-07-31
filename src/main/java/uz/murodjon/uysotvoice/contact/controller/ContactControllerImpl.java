package uz.murodjon.uysotvoice.contact.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.contact.dto.ContactDetail;
import uz.murodjon.uysotvoice.contact.dto.ContactFilter;
import uz.murodjon.uysotvoice.contact.dto.ContactImportResult;
import uz.murodjon.uysotvoice.contact.dto.ContactRow;
import uz.murodjon.uysotvoice.contact.dto.CreateContactRequest;
import uz.murodjon.uysotvoice.contact.dto.UpdateContactRequest;
import uz.murodjon.uysotvoice.contact.service.ContactService;
import uz.murodjon.uysotvoice.donotcall.dto.ContactDncResponse;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class ContactControllerImpl implements ContactController {

    private final ContactService service;

    public ContactControllerImpl(ContactService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<ContactRow>> create(CreateContactRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ContactRow>>> list(ContactFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactDetail>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.detail(id)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactRow>> update(long id, UpdateContactRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactImportResult>> importCsv(String csv) {
        return ResponseEntity.ok(ResponseData.ok(service.importCsv(csv)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.addToDoNotCall(id)));
    }
}
