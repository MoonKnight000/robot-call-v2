package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

@RestController
public class SipTrunkControllerImpl implements SipTrunkController {

    private final SipTrunkUseCase sipTrunkUseCase;

    public SipTrunkControllerImpl(SipTrunkUseCase sipTrunkUseCase) {
        this.sipTrunkUseCase = sipTrunkUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> create(long companyId, CreateSipTrunkRequest request) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(long companyId, SipTrunkFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.requireTrunk(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkStatus>> getStatus(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.getStatus(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<List<SipTrunkStatus>>> getAllStatuses(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.getAllStatuses(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> update(long companyId, long id, UpdateSipTrunkRequest request) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.makeDefault(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(long companyId, long id) {
        sipTrunkUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(new SipTrunkDeleteResponse(id, true)));
    }
}
