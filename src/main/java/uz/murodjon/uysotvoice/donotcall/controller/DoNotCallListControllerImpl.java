package uz.murodjon.uysotvoice.donotcall.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallFilter;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRemoveResponse;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRow;
import uz.murodjon.uysotvoice.donotcall.service.DoNotCallListService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class DoNotCallListControllerImpl implements DoNotCallListController {

    private final DoNotCallListService service;

    public DoNotCallListControllerImpl(DoNotCallListService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<DoNotCallRow>>> list(DoNotCallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<DoNotCallRemoveResponse>> remove(String phone) {
        return ResponseEntity.ok(ResponseData.ok(service.remove(phone)));
    }
}
