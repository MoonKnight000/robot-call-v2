package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;

import java.util.List;

@RestController
public class CallerIdPoolControllerImpl implements CallerIdPoolController {

    private final CallerIdPoolUseCase callerIdPoolUseCase;

    public CallerIdPoolControllerImpl(CallerIdPoolUseCase callerIdPoolUseCase) {
        this.callerIdPoolUseCase = callerIdPoolUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CallerIdPoolDetail>> create(long companyId, CreateCallerIdPoolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callerIdPoolUseCase.createPool(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CallerIdPoolRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(callerIdPoolUseCase.listPools(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<CallerIdPoolDetail>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callerIdPoolUseCase.getPool(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CallerIdPoolDetail>> update(long companyId, long id, UpdateCallerIdPoolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callerIdPoolUseCase.updatePool(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        callerIdPoolUseCase.deletePool(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<CallerIdNumberRow>> addNumber(long companyId, long id, AddCallerIdNumberRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callerIdPoolUseCase.addNumber(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> removeNumber(long companyId, long id, long numberId) {
        callerIdPoolUseCase.removeNumber(companyId, id, numberId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> setCooldown(long companyId, long id, long numberId, int hours) {
        callerIdPoolUseCase.setNumberCooling(companyId, id, numberId, hours);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> activate(long companyId, long id, long numberId) {
        callerIdPoolUseCase.activateNumber(companyId, id, numberId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> disable(long companyId, long id, long numberId) {
        callerIdPoolUseCase.disableNumber(companyId, id, numberId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
