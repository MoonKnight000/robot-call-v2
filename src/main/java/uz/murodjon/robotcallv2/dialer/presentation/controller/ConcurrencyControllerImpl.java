package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.dialer.application.dto.ConcurrencyResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.TrunkCapacityRow;
import uz.murodjon.robotcallv2.dialer.application.port.input.ConcurrencyUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.TrunkCapacityUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ConcurrencyControllerImpl implements ConcurrencyController {

    private final ConcurrencyUseCase concurrencyUseCase;
    private final TrunkCapacityUseCase trunkCapacityUseCase;

    public ConcurrencyControllerImpl(ConcurrencyUseCase concurrencyUseCase, TrunkCapacityUseCase trunkCapacityUseCase) {
        this.concurrencyUseCase = concurrencyUseCase;
        this.trunkCapacityUseCase = trunkCapacityUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ConcurrencyResponse>> find(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(concurrencyUseCase.findConcurrency(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<TrunkCapacityRow>>> findTrunks(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(trunkCapacityUseCase.findTrunkCapacities(companyId)));
    }
}
