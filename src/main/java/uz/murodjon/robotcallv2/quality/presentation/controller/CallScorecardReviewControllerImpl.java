package uz.murodjon.robotcallv2.quality.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewScorecardRequest;
import uz.murodjon.robotcallv2.quality.application.port.input.ScorecardUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CallScorecardReviewControllerImpl implements CallScorecardReviewController {

    private final ScorecardUseCase scorecardUseCase;

    public CallScorecardReviewControllerImpl(ScorecardUseCase scorecardUseCase) {
        this.scorecardUseCase = scorecardUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallScorecard>>> filter(long companyId,
                                                                           CallScorecardFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.filterCallScorecards(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallScorecard>> review(long companyId, long callId,
                                                              ReviewScorecardRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.recordReview(companyId, callId, request)));
    }
}
