package uz.murodjon.robotcallv2.contact.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactFieldUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ContactFieldControllerImpl implements ContactFieldController {

    private final ContactFieldUseCase contactFieldUseCase;

    public ContactFieldControllerImpl(ContactFieldUseCase contactFieldUseCase) {
        this.contactFieldUseCase = contactFieldUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<ContactField>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(contactFieldUseCase.findAllByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactField>> create(long companyId, CreateContactFieldRequest request) {
        return ResponseEntity.ok(ResponseData.ok(contactFieldUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ContactField>> update(long companyId, long id, UpdateContactFieldRequest request) {
        return ResponseEntity.ok(ResponseData.ok(contactFieldUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        contactFieldUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
