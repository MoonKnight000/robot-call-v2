package uz.murodjon.robotcallv2.phonenumber.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.phonenumber.application.dto.AddPoolNumberRequest;
import uz.murodjon.robotcallv2.phonenumber.application.dto.PhoneNumberRow;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PlatformPhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class PlatformPhoneNumberControllerImpl implements PlatformPhoneNumberController {

    private final PlatformPhoneNumberUseCase platformPhoneNumberUseCase;

    public PlatformPhoneNumberControllerImpl(PlatformPhoneNumberUseCase platformPhoneNumberUseCase) {
        this.platformPhoneNumberUseCase = platformPhoneNumberUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PhoneNumberRow>> addPoolNumber(AddPoolNumberRequest request) {
        return ResponseEntity.ok(ResponseData.ok(platformPhoneNumberUseCase.addPoolNumber(request)));
    }

    @Override
    public ResponseEntity<ResponseData<Integer>> addBatchPoolNumbers(List<AddPoolNumberRequest> requests) {
        return ResponseEntity.ok(ResponseData.ok(platformPhoneNumberUseCase.addBatchPoolNumbers(requests)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<PhoneNumberRow>>> findAllPoolNumbers(PhoneNumberFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(platformPhoneNumberUseCase.findAllPoolNumbers(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> removePoolNumber(long id) {
        platformPhoneNumberUseCase.removePoolNumber(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
