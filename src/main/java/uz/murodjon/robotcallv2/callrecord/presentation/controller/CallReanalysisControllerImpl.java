package uz.murodjon.robotcallv2.callrecord.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallReanalysisPreviewResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.CreateCallReanalysisRequest;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallReanalysisUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisFilter;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CallReanalysisControllerImpl implements CallReanalysisController {

    private final CallReanalysisUseCase callReanalysisUseCase;

    public CallReanalysisControllerImpl(CallReanalysisUseCase callReanalysisUseCase) {
        this.callReanalysisUseCase = callReanalysisUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CallReanalysisPreviewResponse>> preview(long companyId,
                                                                               CreateCallReanalysisRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callReanalysisUseCase.previewReanalysis(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CallReanalysisJob>> create(long companyId, CreateCallReanalysisRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callReanalysisUseCase.createReanalysis(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallReanalysisJob>>> filter(long companyId,
                                                                              CallReanalysisFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(callReanalysisUseCase.filterReanalyses(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallReanalysisJob>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callReanalysisUseCase.findReanalysis(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CallReanalysisJob>> cancel(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callReanalysisUseCase.cancelReanalysis(companyId, id)));
    }
}
