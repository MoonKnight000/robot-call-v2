package uz.murodjon.robotcallv2.inbound.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.inbound.application.dto.SaveCallerRuleRequest;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundCallerRuleUseCase;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRuleFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class InboundCallerRuleControllerImpl implements InboundCallerRuleController {

    private final InboundCallerRuleUseCase inboundCallerRuleUseCase;

    public InboundCallerRuleControllerImpl(InboundCallerRuleUseCase inboundCallerRuleUseCase) {
        this.inboundCallerRuleUseCase = inboundCallerRuleUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<InboundCallerRule>> create(long companyId, SaveCallerRuleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(inboundCallerRuleUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<InboundCallerRule>>> list(long companyId,
                                                                              InboundCallerRuleFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(inboundCallerRuleUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<InboundCallerRule>> update(long companyId, long id, SaveCallerRuleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(inboundCallerRuleUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        inboundCallerRuleUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
