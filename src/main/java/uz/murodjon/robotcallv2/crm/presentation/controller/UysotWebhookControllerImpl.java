package uz.murodjon.robotcallv2.crm.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.crm.application.dto.UysotLeadWebhookEvent;
import uz.murodjon.robotcallv2.crm.application.dto.UysotWebhookResult;
import uz.murodjon.robotcallv2.crm.application.port.input.UysotWebhookUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class UysotWebhookControllerImpl implements UysotWebhookController {

    private final UysotWebhookUseCase uysotWebhookUseCase;

    public UysotWebhookControllerImpl(UysotWebhookUseCase uysotWebhookUseCase) {
        this.uysotWebhookUseCase = uysotWebhookUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<UysotWebhookResult>> handleWebhook(String secret, UysotLeadWebhookEvent event) {
        return ResponseEntity.ok(ResponseData.ok(uysotWebhookUseCase.handleWebhook(event, secret)));
    }
}
