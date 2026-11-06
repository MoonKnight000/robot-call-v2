package uz.murodjon.robotcallv2.quality.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.quality.application.dto.ScorecardSettingsResponse;
import uz.murodjon.robotcallv2.quality.application.dto.UpdateScorecardSettingsRequest;
import uz.murodjon.robotcallv2.quality.application.port.input.ScorecardUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ScorecardSettingsControllerImpl implements ScorecardSettingsController {

    private final ScorecardUseCase scorecardUseCase;

    public ScorecardSettingsControllerImpl(ScorecardUseCase scorecardUseCase) {
        this.scorecardUseCase = scorecardUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ScorecardSettingsResponse>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.findSettings(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<ScorecardSettingsResponse>> update(long companyId,
                                                                         UpdateScorecardSettingsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.updateSettings(companyId, request)));
    }
}
