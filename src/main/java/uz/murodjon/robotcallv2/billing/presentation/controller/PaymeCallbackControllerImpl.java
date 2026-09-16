package uz.murodjon.robotcallv2.billing.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.billing.application.dto.PaymeResponse;
import uz.murodjon.robotcallv2.billing.application.port.input.PaymeMerchantUseCase;

@RestController
public class PaymeCallbackControllerImpl implements PaymeCallbackController {

    private final PaymeMerchantUseCase paymeMerchantUseCase;

    public PaymeCallbackControllerImpl(PaymeMerchantUseCase paymeMerchantUseCase) {
        this.paymeMerchantUseCase = paymeMerchantUseCase;
    }

    @Override
    public ResponseEntity<PaymeResponse> handleCallback(String authorization, String body) {
        return ResponseEntity.ok(paymeMerchantUseCase.handle(authorization, body));
    }
}
