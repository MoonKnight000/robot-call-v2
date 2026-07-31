package uz.murodjon.uysotvoice.operator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.agent.dialog.OperatorSnapshot;
import uz.murodjon.uysotvoice.operator.service.OperatorService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class OperatorControllerImpl implements OperatorController {

    private final OperatorService service;

    public OperatorControllerImpl(OperatorService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<OperatorSnapshot>> context(String channelId) {
        return ResponseEntity.ok(ResponseData.ok(service.snapshot(channelId)));
    }
}
