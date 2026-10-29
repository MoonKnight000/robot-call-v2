package uz.murodjon.robotcallv2.siptrunk.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CreateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkDeleteResponse;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkStatus;
import uz.murodjon.robotcallv2.siptrunk.application.dto.UpdateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.PlatformSipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

@RestController
public class PlatformSipTrunkControllerImpl implements PlatformSipTrunkController {

    private final PlatformSipTrunkUseCase platformSipTrunkUseCase;

    public PlatformSipTrunkControllerImpl(PlatformSipTrunkUseCase platformSipTrunkUseCase) {
        this.platformSipTrunkUseCase = platformSipTrunkUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> create(long companyId, CreateSipTrunkRequest request) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(long companyId, SipTrunkFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<List<SipTrunkStatus>>> getAllStatuses(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.findAllStatuses(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.requireTrunk(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> update(long companyId, long id, UpdateSipTrunkRequest request) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.makeDefault(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(platformSipTrunkUseCase.delete(companyId, id)));
    }
}
