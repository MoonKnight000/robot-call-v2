package uz.murodjon.robotcallv2.quality.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.quality.application.dto.SaveScorecardCriterionRequest;
import uz.murodjon.robotcallv2.quality.application.port.input.ScorecardUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterionFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ScorecardCriterionControllerImpl implements ScorecardCriterionController {

    private final ScorecardUseCase scorecardUseCase;

    public ScorecardCriterionControllerImpl(ScorecardUseCase scorecardUseCase) {
        this.scorecardUseCase = scorecardUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ScorecardCriterion>> create(long companyId,
                                                                  SaveScorecardCriterionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.createCriterion(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ScorecardCriterion>>> list(long companyId,
                                                                              ScorecardCriterionFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.listCriteria(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ScorecardCriterion>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.requireCriterion(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<ScorecardCriterion>> update(long companyId, long id,
                                                                  SaveScorecardCriterionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scorecardUseCase.updateCriterion(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        scorecardUseCase.deleteCriterion(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
