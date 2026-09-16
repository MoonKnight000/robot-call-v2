package uz.murodjon.robotcallv2.quality.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewCallRequest;
import uz.murodjon.robotcallv2.quality.application.port.input.CallReviewUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.CallReviewFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CallReviewControllerImpl implements CallReviewController {

    private final CallReviewUseCase callReviewUseCase;

    public CallReviewControllerImpl(CallReviewUseCase callReviewUseCase) {
        this.callReviewUseCase = callReviewUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallQuality>>> filter(long companyId, CallReviewFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(callReviewUseCase.filterReviews(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallQuality>> get(long companyId, long callId) {
        return ResponseEntity.ok(ResponseData.ok(callReviewUseCase.findReview(companyId, callId)));
    }

    @Override
    public ResponseEntity<ResponseData<CallQuality>> review(long companyId, long callId, ReviewCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callReviewUseCase.recordReview(companyId, callId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CallQuality>> createKnowledgeItem(long companyId, long callId,
                                                                        KnowledgeItemCreateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callReviewUseCase.createKnowledgeItem(companyId, callId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CallQuality>> createTestCase(long companyId, long callId,
                                                                   SaveAgentTestCaseRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callReviewUseCase.createTestCase(companyId, callId, request)));
    }
}
