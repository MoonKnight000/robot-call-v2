package uz.murodjon.robotcallv2.sms.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.sms.application.dto.ConnectEskizRequest;
import uz.murodjon.robotcallv2.sms.application.dto.EskizConnectionRow;
import uz.murodjon.robotcallv2.sms.application.port.input.EskizConnectionUseCase;

@RestController
public class EskizConnectionControllerImpl implements EskizConnectionController {

    private final EskizConnectionUseCase eskizConnectionUseCase;

    public EskizConnectionControllerImpl(EskizConnectionUseCase eskizConnectionUseCase) {
        this.eskizConnectionUseCase = eskizConnectionUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<EskizConnectionRow>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(eskizConnectionUseCase.findByCompanyId(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<EskizConnectionRow>> connect(long companyId, ConnectEskizRequest request) {
        return ResponseEntity.ok(ResponseData.ok(eskizConnectionUseCase.connect(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> disconnect(long companyId) {
        eskizConnectionUseCase.disconnect(companyId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
