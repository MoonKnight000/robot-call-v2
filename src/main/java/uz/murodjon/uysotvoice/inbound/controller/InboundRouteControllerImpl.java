package uz.murodjon.uysotvoice.inbound.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.inbound.dto.CreateInboundRouteRequest;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteFilter;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteRow;
import uz.murodjon.uysotvoice.inbound.dto.UpdateInboundRouteRequest;
import uz.murodjon.uysotvoice.inbound.service.InboundRouteService;
import uz.murodjon.uysotvoice.report.dto.InboundRouteStats;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class InboundRouteControllerImpl implements InboundRouteController {

    private final InboundRouteService service;

    public InboundRouteControllerImpl(InboundRouteService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> create(CreateInboundRouteRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<InboundRouteRow>>> list(InboundRouteFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.routeRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> update(long id, UpdateInboundRouteRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> disable(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.disable(id)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteStats>> stats(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.stats(id)));
    }
}
