package uz.murodjon.robotcallv2.operator.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.Map;

@RestController
public class OperatorControllerImpl implements OperatorController {

    private final OperatorUseCase service;

    public OperatorControllerImpl(OperatorUseCase service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<OperatorSnapshot>> context(String channelId) {
        return ResponseEntity.ok(ResponseData.ok(service.snapshot(channelId)));
    }

    @Override
    public ResponseEntity<ResponseData<Map<String, Object>>> takeover(String channelId, String extension) {
        service.takeover(channelId, extension);
        return ResponseEntity.ok(ResponseData.ok(Map.of(
                "channelId", channelId,
                "status", "TRANSFERRED",
                "operatorExtension", extension
        )));
    }

    @Override
    public ResponseEntity<ResponseData<Map<String, Object>>> whisper(String channelId, Map<String, String> body) {
        String message = body != null ? body.getOrDefault("message", "") : "";
        service.whisper(channelId, message);
        return ResponseEntity.ok(ResponseData.ok(Map.of(
                "channelId", channelId,
                "status", "DELIVERED",
                "message", message
        )));
    }
}
