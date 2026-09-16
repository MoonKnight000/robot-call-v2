package uz.murodjon.robotcallv2.billing.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.billing.application.dto.ClickResponse;
import uz.murodjon.robotcallv2.billing.application.port.input.ClickMerchantUseCase;

import java.util.Map;

@RestController
public class ClickCallbackControllerImpl implements ClickCallbackController {

    private final ClickMerchantUseCase clickMerchantUseCase;

    public ClickCallbackControllerImpl(ClickMerchantUseCase clickMerchantUseCase) {
        this.clickMerchantUseCase = clickMerchantUseCase;
    }

    @Override
    public ResponseEntity<ClickResponse> prepare(Map<String, String> form) {
        return ResponseEntity.ok(clickMerchantUseCase.prepare(form));
    }

    @Override
    public ResponseEntity<ClickResponse> complete(Map<String, String> form) {
        return ResponseEntity.ok(clickMerchantUseCase.complete(form));
    }
}
