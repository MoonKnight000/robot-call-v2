package uz.murodjon.robotcallv2.billing.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.billing.application.dto.ModelPriceResponse;
import uz.murodjon.robotcallv2.billing.application.dto.UpsertModelPriceRequest;
import uz.murodjon.robotcallv2.billing.application.port.input.ModelPriceUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ModelPriceControllerImpl implements ModelPriceController {

    private final ModelPriceUseCase modelPriceUseCase;

    public ModelPriceControllerImpl(ModelPriceUseCase modelPriceUseCase) {
        this.modelPriceUseCase = modelPriceUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<ModelPriceResponse>>> findAll() {
        return ResponseEntity.ok(ResponseData.ok(modelPriceUseCase.findAll()));
    }

    @Override
    public ResponseEntity<ResponseData<ModelPriceResponse>> upsert(long companyId, String model,
                                                                  UpsertModelPriceRequest request) {
        return ResponseEntity.ok(ResponseData.ok(modelPriceUseCase.upsert(companyId, model, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, String model) {
        modelPriceUseCase.delete(companyId, model);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
