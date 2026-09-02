package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.util.List;

@RestController
public class SipTrunkControllerImpl implements SipTrunkController {

    private final SipTrunkUseCase sipTrunkUseCase;

    public SipTrunkControllerImpl(SipTrunkUseCase sipTrunkUseCase) {
        this.sipTrunkUseCase = sipTrunkUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> create(CreateSipTrunkRequest r) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(SipTrunkFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.requireTrunk(id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkStatus>> getStatus(long id) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.getStatus(id)));
    }

    @Override
    public ResponseEntity<ResponseData<List<SipTrunkStatus>>> getAllStatuses() {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.getAllStatuses()));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> update(long id, UpdateSipTrunkRequest r) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(long id) {
        return ResponseEntity.ok(ResponseData.ok(sipTrunkUseCase.makeDefault(id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(long id) {
        sipTrunkUseCase.delete(id);
        return ResponseEntity.ok(ResponseData.ok(new SipTrunkDeleteResponse(id, true)));
    }
}
