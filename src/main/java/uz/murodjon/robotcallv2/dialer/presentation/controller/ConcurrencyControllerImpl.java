package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.dialer.application.dto.ConcurrencyResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.ConcurrencyUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ConcurrencyControllerImpl implements ConcurrencyController {

    private final ConcurrencyUseCase concurrencyUseCase;

    public ConcurrencyControllerImpl(ConcurrencyUseCase concurrencyUseCase) {
        this.concurrencyUseCase = concurrencyUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ConcurrencyResponse>> find(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(concurrencyUseCase.findConcurrency(companyId)));
    }
}
