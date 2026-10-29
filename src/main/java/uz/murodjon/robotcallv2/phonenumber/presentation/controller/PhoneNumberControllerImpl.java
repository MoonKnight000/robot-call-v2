package uz.murodjon.robotcallv2.phonenumber.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.phonenumber.application.dto.*;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class PhoneNumberControllerImpl implements PhoneNumberController {

    private final PhoneNumberUseCase phoneNumberUseCase;

    public PhoneNumberControllerImpl(PhoneNumberUseCase phoneNumberUseCase) {
        this.phoneNumberUseCase = phoneNumberUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<PhoneNumberRow>>> findMarketplaceNumbers(PhoneNumberFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(phoneNumberUseCase.findMarketplaceNumbers(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<PhoneNumberRow>> purchaseNumber(long companyId, PurchaseNumberRequest request) {
        return ResponseEntity.ok(ResponseData.ok(phoneNumberUseCase.purchaseNumber(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<PhoneNumberRow>>> findCompanyNumbers(long companyId, PhoneNumberFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(phoneNumberUseCase.findCompanyNumbers(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<PhoneNumberRow>> updateInboundRouting(long companyId, long id, UpdateInboundRoutingRequest request) {
        return ResponseEntity.ok(ResponseData.ok(phoneNumberUseCase.updateInboundRouting(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> releaseNumber(long companyId, long id) {
        phoneNumberUseCase.releaseNumber(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<TestSipConnectionResponse>> testSipConnection(TestSipConnectionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(phoneNumberUseCase.testSipConnection(request)));
    }
}
