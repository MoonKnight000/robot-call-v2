package uz.murodjon.robotcallv2.contact.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ContactControllerImpl implements ContactController {

    private final ContactUseCase contactUseCase;

    public ContactControllerImpl(ContactUseCase contactUseCase) {
        this.contactUseCase = contactUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<Contact>> create(CreateContactRequest r) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<Contact>>> list(ContactFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactDetail>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.detail(id)));
    }

    @Override
    public ResponseEntity<ResponseData<Contact>> update(long id, UpdateContactRequest r) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long id) {
        contactUseCase.delete(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<ContactImportResult>> importCsv(String csv) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.importCsv(csv)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(long id) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.addToDoNotCall(id)));
    }
}
