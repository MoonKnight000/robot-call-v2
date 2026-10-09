package uz.murodjon.robotcallv2.quality.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.quality.application.port.input.ScorecardUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class CallScorecardControllerImpl implements CallScorecardController {

    private final ScorecardUseCase scorecardUseCase;

    public CallScorecardControllerImpl(ScorecardUseCase scorecardUseCase) {
        this.scorecardUseCase = scorecardUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CallScorecard>> get(long companyId, long callId) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.requireCallScorecard(companyId, callId)));
    }
}
