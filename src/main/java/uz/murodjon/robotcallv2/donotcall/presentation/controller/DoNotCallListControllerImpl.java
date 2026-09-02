package uz.murodjon.robotcallv2.donotcall.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class DoNotCallListControllerImpl implements DoNotCallListController {

    private final DoNotCallUseCase doNotCallUseCase;

    public DoNotCallListControllerImpl(DoNotCallUseCase doNotCallUseCase) {
        this.doNotCallUseCase = doNotCallUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<DoNotCallRow>>> list(DoNotCallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(doNotCallUseCase.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<DoNotCallRemoveResponse>> remove(String phone) {
        return ResponseEntity.ok(ResponseData.ok(doNotCallUseCase.remove(phone)));
    }
}
