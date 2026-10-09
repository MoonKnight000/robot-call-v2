package uz.murodjon.robotcallv2.wrapup.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.wrapup.application.dto.SaveWrapUpCodeRequest;
import uz.murodjon.robotcallv2.wrapup.application.port.input.WrapUpUseCase;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCodeFilter;

@RestController
public class WrapUpCodeControllerImpl implements WrapUpCodeController {

    private final WrapUpUseCase wrapUpUseCase;

    public WrapUpCodeControllerImpl(WrapUpUseCase wrapUpUseCase) {
        this.wrapUpUseCase = wrapUpUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<WrapUpCode>> create(long companyId, SaveWrapUpCodeRequest request) {
        return ResponseEntity.ok(ResponseData.ok(wrapUpUseCase.createCode(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<WrapUpCode>>> list(long companyId, WrapUpCodeFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(wrapUpUseCase.listCodes(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<WrapUpCode>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(wrapUpUseCase.requireCode(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<WrapUpCode>> update(long companyId, long id, SaveWrapUpCodeRequest request) {
        return ResponseEntity.ok(ResponseData.ok(wrapUpUseCase.updateCode(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        wrapUpUseCase.deleteCode(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
