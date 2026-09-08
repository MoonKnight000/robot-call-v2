package uz.murodjon.robotcallv2.contact.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.contact.application.dto.ContactDetail;
import uz.murodjon.robotcallv2.contact.application.dto.ContactImportResult;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactRequest;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
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
    public ResponseEntity<ResponseData<Contact>> create(long companyId, CreateContactRequest request) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<Contact>>> list(long companyId, ContactFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactDetail>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.detail(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<Contact>> update(long companyId, long id, UpdateContactRequest request) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        contactUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<ContactImportResult>> importCsv(long companyId, String csv) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.importCsv(companyId, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactDncResponse>> addToDoNotCall(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(contactUseCase.addToDoNotCall(companyId, id)));
    }
}
