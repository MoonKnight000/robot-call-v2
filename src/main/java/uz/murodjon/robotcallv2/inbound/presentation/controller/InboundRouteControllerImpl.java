package uz.murodjon.robotcallv2.inbound.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class InboundRouteControllerImpl implements InboundRouteController {

    private final InboundRouteUseCase inboundRouteUseCase;

    public InboundRouteControllerImpl(InboundRouteUseCase inboundRouteUseCase) {
        this.inboundRouteUseCase = inboundRouteUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> create(CreateInboundRouteRequest r) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<InboundRouteRow>>> list(InboundRouteFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.routeRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> update(long id, UpdateInboundRouteRequest r) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.update(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> disable(long id) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.disable(id)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteStats>> stats(long id) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.stats(id)));
    }
}

