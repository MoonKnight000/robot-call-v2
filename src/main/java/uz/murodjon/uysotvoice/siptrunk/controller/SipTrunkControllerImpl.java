package uz.murodjon.uysotvoice.siptrunk.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.siptrunk.dto.CreateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkDeleteResponse;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkFilter;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkRow;
import uz.murodjon.uysotvoice.siptrunk.dto.UpdateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.service.SipTrunkService;

@RestController
public class SipTrunkControllerImpl implements SipTrunkController {

    private final SipTrunkService service;

    public SipTrunkControllerImpl(SipTrunkService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> create(CreateSipTrunkRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<SipTrunkRow>>> list(SipTrunkFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.requireTrunk(id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> update(long id, UpdateSipTrunkRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkRow>> makeDefault(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.makeDefault(id)));
    }

    @Override
    public ResponseEntity<ResponseData<SipTrunkDeleteResponse>> delete(long id) {
        service.delete(id);
        return ResponseEntity.ok(ResponseData.ok(new SipTrunkDeleteResponse(id, true)));
    }
}
