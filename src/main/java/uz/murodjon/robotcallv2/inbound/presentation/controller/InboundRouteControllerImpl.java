package uz.murodjon.robotcallv2.inbound.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
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
    public ResponseEntity<ResponseData<InboundRouteRow>> create(long companyId, CreateInboundRouteRequest request) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<InboundRouteRow>>> list(long companyId, InboundRouteFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.routeRow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> update(long companyId, long id, UpdateInboundRouteRequest request) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteRow>> disable(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.disable(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundRouteStats>> stats(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(inboundRouteUseCase.stats(companyId, id)));
    }
}

