package uz.murodjon.robotcallv2.conversion.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.conversion.application.dto.ConversionGoalRequest;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionResultResponse;
import uz.murodjon.robotcallv2.conversion.application.dto.ReportConversionRequest;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ConversionControllerImpl implements ConversionController {

    private final ConversionUseCase conversionUseCase;

    public ConversionControllerImpl(ConversionUseCase conversionUseCase) {
        this.conversionUseCase = conversionUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<ConversionGoal>>> goals(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(conversionUseCase.findGoals(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<ConversionGoal>> upsertGoal(long companyId,
                                                                   ConversionGoalRequest request) {
        return ResponseEntity.ok(ResponseData.ok(conversionUseCase.upsertGoal(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteGoal(long companyId, long id) {
        conversionUseCase.deleteGoal(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<ConversionResultResponse>> reportConversion(
            long companyId, ReportConversionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(conversionUseCase.reportConversion(companyId, request)));
    }
}
